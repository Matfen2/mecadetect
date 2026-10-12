package fr.mecadetect.intervention;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.equipement.StatutEquipement;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tâches 3.5 et 3.7 : démarrage et clôture par le technicien (RG-02, RG-03, RG-04, RG-05). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InterventionRealisationApiTest {

    private static final LocalDateTime DEMANDE = LocalDateTime.of(2026, 1, 5, 7, 30);
    private static final LocalDateTime DEBUT = LocalDateTime.of(2026, 1, 5, 8, 0);

    @Autowired private WebApplicationContext context;
    @Autowired private InterventionRepository interventionRepository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private MockMvc mockMvc;
    private Zone zone;
    private TypeEquipement type;
    private Equipement presse;
    private Utilisateur admin;
    private Utilisateur demandeur;
    private Utilisateur technicien;
    private Utilisateur collegue;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        type = typeRepository.saveAndFlush(new TypeEquipement("Type de test"));
        presse = equipement("TST-001");
        admin = utilisateur("admin.real@mecadetect.fr", Role.ADMIN);
        demandeur = utilisateur("demandeur.real@mecadetect.fr", Role.DEMANDEUR);
        technicien = utilisateur("technicien.real@mecadetect.fr", Role.TECHNICIEN);
        collegue = utilisateur("collegue.real@mecadetect.fr", Role.TECHNICIEN);
    }

    private Equipement equipement(String reference) {
        return equipementRepository.saveAndFlush(
                new Equipement(reference, "Équipement " + reference, LocalDate.of(2020, 1, 6), type, zone));
    }

    private Utilisateur utilisateur(String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur("Test", "Utilisateur", email, "hash", role));
    }

    /** Intervention DEMANDEE sur l'équipement, enregistrée telle quelle (version 0). */
    private Intervention demandee(Equipement equipement) {
        return new Intervention(interventionRepository.prochainNumero(2026), "Fuite d'huile",
                "Fuite sur le vérin principal", Priorite.HAUTE, equipement, demandeur, DEMANDE);
    }

    private Intervention enregistrer(Intervention intervention) {
        return interventionRepository.saveAndFlush(intervention);
    }

    private Intervention planifiee(Equipement equipement, Utilisateur assigne) {
        var intervention = demandee(equipement);
        intervention.planifier(assigne, DEBUT, admin);
        return enregistrer(intervention);
    }

    private Intervention enCours(Equipement equipement, Utilisateur assigne) {
        var intervention = demandee(equipement);
        intervention.planifier(assigne, DEBUT, admin);
        intervention.demarrer(assigne, DEBUT);
        return enregistrer(intervention);
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    private ResultActions demarrer(Utilisateur auteur, Intervention intervention) throws Exception {
        return mockMvc.perform(post("/api/v1/interventions/{id}/demarrer", intervention.getId())
                .with(connecte(auteur))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": " + intervention.getVersion() + "}"));
    }

    private ResultActions cloturer(Utilisateur auteur, Long idIntervention, long version, String compteRendu,
                                   int tempsPasseMin) throws Exception {
        return mockMvc.perform(post("/api/v1/interventions/{id}/cloturer", idIntervention)
                .with(connecte(auteur))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "compteRendu": "%s", "tempsPasseMin": %d}
                        """.formatted(version, compteRendu, tempsPasseMin)));
    }

    private StatutEquipement statutDe(Equipement equipement) {
        return equipementRepository.findById(equipement.getId()).orElseThrow().getStatut();
    }

    // ------------------------------------------------------------------ démarrage

    @Test
    @DisplayName("Le technicien assigné démarre : EN_COURS et équipement EN_MAINTENANCE (RG-05)")
    void demarrage() throws Exception {
        var intervention = planifiee(presse, technicien);

        demarrer(technicien, intervention)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.dateDebut").isNotEmpty())
                .andExpect(jsonPath("$.equipement.statut").value("EN_MAINTENANCE"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(statutDe(presse)).isEqualTo(StatutEquipement.EN_MAINTENANCE);
    }

    @Test
    @DisplayName("RG-02 : un autre technicien ne peut pas démarrer l'intervention : 403")
    void demarrageParUnCollegue() throws Exception {
        var intervention = planifiee(presse, technicien);

        demarrer(collegue, intervention)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TECHNICIEN_NON_ASSIGNE"));
    }

    @Test
    @DisplayName("RG-04 : un technicien ne peut avoir qu'une intervention EN_COURS : 409")
    void uneSeuleInterventionEnCours() throws Exception {
        enCours(equipement("TST-002"), technicien);
        var intervention = planifiee(presse, technicien);

        demarrer(technicien, intervention)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERVENTION_DEJA_EN_COURS"));
    }

    @Test
    @DisplayName("Un admin ne démarre pas une intervention : 403")
    void demarrageParAdmin() throws Exception {
        var intervention = planifiee(presse, technicien);

        demarrer(admin, intervention).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Une intervention non planifiée ne peut pas être démarrée : 409")
    void demarrageSansPlanification() throws Exception {
        var intervention = enregistrer(demandee(presse));

        demarrer(technicien, intervention)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSITION_INTERDITE"));
    }

    // ------------------------------------------------------------------ clôture

    @Test
    @DisplayName("Clôture : TERMINEE avec compte rendu et temps passé, équipement EN_SERVICE (RG-05)")
    void cloture() throws Exception {
        var intervention = enCours(presse, technicien);

        cloturer(technicien, intervention.getId(), 0, "Joint du vérin remplacé, essai concluant", 90)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TERMINEE"))
                .andExpect(jsonPath("$.compteRendu").value("Joint du vérin remplacé, essai concluant"))
                .andExpect(jsonPath("$.tempsPasseMin").value(90))
                .andExpect(jsonPath("$.dateFin").isNotEmpty())
                .andExpect(jsonPath("$.equipement.statut").value("EN_SERVICE"));

        assertThat(statutDe(presse)).isEqualTo(StatutEquipement.EN_SERVICE);
    }

    @Test
    @DisplayName("RG-05 : si une autre panne reste ouverte, l'équipement repasse EN_PANNE et non EN_SERVICE")
    void clotureAvecUneAutrePanneOuverte() throws Exception {
        var intervention = enCours(presse, technicien);
        enregistrer(demandee(presse));

        cloturer(technicien, intervention.getId(), 0, "Joint du vérin remplacé, essai concluant", 90)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipement.statut").value("EN_PANNE"));
    }

    @Test
    @DisplayName("RG-03 : compte rendu trop court et temps passé nul : 400")
    void clotureIncomplete() throws Exception {
        var intervention = enCours(presse, technicien);

        cloturer(technicien, intervention.getId(), 0, "Fait", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DONNEES_INVALIDES"))
                .andExpect(jsonPath("$.erreurs.length()").value(2));
    }

    @Test
    @DisplayName("UC-DB6 : une clôture avec une version périmée est refusée : 409")
    void clotureVersionPerimee() throws Exception {
        var intervention = enCours(presse, technicien);

        cloturer(technicien, intervention.getId(), 5, "Joint du vérin remplacé, essai concluant", 90)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLIT_VERSION"));
    }
}