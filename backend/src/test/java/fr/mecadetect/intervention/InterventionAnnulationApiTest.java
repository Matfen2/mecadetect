package fr.mecadetect.intervention;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tâche 3.6 : annulation avec motif par l'admin (RG-01, RG-03, RG-05). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InterventionAnnulationApiTest {

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2026, 1, 5, 8, 0);

    @Autowired private WebApplicationContext context;
    @Autowired private InterventionRepository interventionRepository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private MockMvc mockMvc;
    private Equipement presse;
    private Utilisateur admin;
    private Utilisateur demandeur;
    private Utilisateur technicien;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        var type = typeRepository.saveAndFlush(new TypeEquipement("Type de test"));
        presse = new Equipement("TST-001", "Presse de test", LocalDate.of(2020, 1, 6), type, zone);
        presse.signalerPanne();
        presse = equipementRepository.saveAndFlush(presse);
        admin = utilisateur("admin.annul@mecadetect.fr", Role.ADMIN);
        demandeur = utilisateur("demandeur.annul@mecadetect.fr", Role.DEMANDEUR);
        technicien = utilisateur("technicien.annul@mecadetect.fr", Role.TECHNICIEN);
    }

    private Utilisateur utilisateur(String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur("Test", "Utilisateur", email, "hash", role));
    }

    private Intervention demandee() {
        return new Intervention(interventionRepository.prochainNumero(2026), "Fuite d'huile",
                "Fuite sur le vérin principal", Priorite.HAUTE, presse, demandeur, LUNDI_8H);
    }

    private Intervention enregistrer(Intervention intervention) {
        return interventionRepository.saveAndFlush(intervention);
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    private ResultActions annuler(Utilisateur auteur, Long idIntervention, long version, String motif)
            throws Exception {
        return mockMvc.perform(post("/api/v1/interventions/{id}/annuler", idIntervention)
                .with(connecte(auteur))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "motif": "%s"}
                        """.formatted(version, motif)));
    }

    @Test
    @DisplayName("L'admin annule une demande : ANNULEE avec motif, l'équipement repasse EN_SERVICE (RG-05)")
    void annulationDUneDemande() throws Exception {
        var intervention = enregistrer(demandee());

        annuler(admin, intervention.getId(), 0, "Panne résolue par l'opérateur")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"))
                .andExpect(jsonPath("$.motifAnnulation").value("Panne résolue par l'opérateur"))
                .andExpect(jsonPath("$.equipement.statut").value("EN_SERVICE"));
    }

    @Test
    @DisplayName("Une intervention planifiée peut aussi être annulée")
    void annulationDUneInterventionPlanifiee() throws Exception {
        var intervention = demandee();
        intervention.planifier(technicien, LUNDI_8H, admin);
        intervention = enregistrer(intervention);

        annuler(admin, intervention.getId(), 0, "Doublon de la demande précédente")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"));
    }

    @Test
    @DisplayName("Une intervention en cours ne peut pas être annulée : 409")
    void annulationDUneInterventionEnCours() throws Exception {
        var intervention = demandee();
        intervention.planifier(technicien, LUNDI_8H, admin);
        intervention.demarrer(technicien, LUNDI_8H);
        intervention = enregistrer(intervention);

        annuler(admin, intervention.getId(), 0, "Changement de priorité")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSITION_INTERDITE"));
    }

    @Test
    @DisplayName("RG-03 : un motif trop court est refusé : 400")
    void motifTropCourt() throws Exception {
        var intervention = enregistrer(demandee());

        annuler(admin, intervention.getId(), 0, "Non")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DONNEES_INVALIDES"));
    }

    @Test
    @DisplayName("RG-01 : un demandeur ne peut pas annuler : 403")
    void annulationParDemandeur() throws Exception {
        var intervention = enregistrer(demandee());

        annuler(demandeur, intervention.getId(), 0, "Je me suis trompé d'équipement")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UC-DB6 : une annulation avec une version périmée est refusée : 409")
    void versionPerimee() throws Exception {
        var intervention = enregistrer(demandee());

        annuler(admin, intervention.getId(), 3, "Panne résolue par l'opérateur")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLIT_VERSION"));
    }

    @Test
    @DisplayName("RG-05 : si une autre panne reste ouverte, l'équipement reste EN_PANNE")
    void annulationAvecUneAutrePanneOuverte() throws Exception {
        var intervention = enregistrer(demandee());
        enregistrer(demandee());

        annuler(admin, intervention.getId(), 0, "Doublon de la demande précédente")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipement.statut").value("EN_PANNE"));
    }
}