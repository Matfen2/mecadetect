package fr.mecadetect.intervention;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import fr.mecadetect.utilisateur.Habilitation;
import fr.mecadetect.utilisateur.HabilitationRepository;
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

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tâche 3.4 : planification et assignation (RG-01, RG-09, UC-DB6). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InterventionPlanificationApiTest {

    @Autowired private WebApplicationContext context;
    @Autowired private InterventionRepository interventionRepository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;

    private MockMvc mockMvc;
    private Utilisateur admin;
    private Utilisateur technicienHabilite;
    private Utilisateur technicienNonHabilite;
    private Utilisateur demandeur;
    private Intervention intervention;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        var type = typeRepository.saveAndFlush(new TypeEquipement("Type de test"));
        var equipement = equipementRepository.saveAndFlush(
                new Equipement("TST-001", "Presse de test", LocalDate.of(2020, 1, 6), type, zone));

        admin = utilisateur("admin.planif@mecadetect.fr", Role.ADMIN);
        demandeur = utilisateur("demandeur.planif@mecadetect.fr", Role.DEMANDEUR);
        technicienHabilite = utilisateur("habilite.planif@mecadetect.fr", Role.TECHNICIEN);
        technicienNonHabilite = utilisateur("non-habilite.planif@mecadetect.fr", Role.TECHNICIEN);
        habilitationRepository.saveAndFlush(new Habilitation(type, technicienHabilite, LocalDate.of(2025, 1, 6)));

        intervention = interventionRepository.saveAndFlush(new Intervention(
                interventionRepository.prochainNumero(2026), "Fuite d'huile", "Fuite sur le vérin principal",
                Priorite.HAUTE, equipement, demandeur, LocalDateTime.of(2026, 10, 12, 8, 0)));
    }

    private Utilisateur utilisateur(String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur("Test", "Utilisateur", email, "hash", role));
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    private ResultActions planifier(Utilisateur auteur, Long idIntervention, long version, Long idTechnicien)
            throws Exception {
        return mockMvc.perform(post("/api/v1/interventions/{id}/planification", idIntervention)
                .with(connecte(auteur))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "idTechnicien": %d, "datePlanifiee": "2026-10-13T10:00:00+02:00"}
                        """.formatted(version, idTechnicien)));
    }

    @Test
    @DisplayName("L'admin planifie avec un technicien habilité : 200, PLANIFIEE, version incrémentée")
    void planification() throws Exception {
        planifier(admin, intervention.getId(), 0, technicienHabilite.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PLANIFIEE"))
                .andExpect(jsonPath("$.technicien.idUtilisateur").value(technicienHabilite.getId().intValue()))
                // 10h à Paris (UTC+2) = 8h UTC
                .andExpect(jsonPath("$.datePlanifiee").value(matchesPattern("2026-10-13T08:00(:00)?Z")))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("RG-09 : un technicien non habilité sur ce type d'équipement est refusé : 409")
    void technicienNonHabilite() throws Exception {
        planifier(admin, intervention.getId(), 0, technicienNonHabilite.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TECHNICIEN_NON_HABILITE"));
    }

    @Test
    @DisplayName("RG-01 : un technicien ne peut pas planifier : 403")
    void planificationParTechnicien() throws Exception {
        planifier(technicienHabilite, intervention.getId(), 0, technicienHabilite.getId())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RG-01 : un demandeur ne peut pas planifier : 403")
    void planificationParDemandeur() throws Exception {
        planifier(demandeur, intervention.getId(), 0, technicienHabilite.getId())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UC-DB6 : une version périmée est refusée : 409 CONFLIT_VERSION")
    void versionPerimee() throws Exception {
        planifier(admin, intervention.getId(), 7, technicienHabilite.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLIT_VERSION"));
    }

    @Test
    @DisplayName("Une intervention déjà planifiée ne peut pas l'être une seconde fois : 409")
    void dejaPlanifiee() throws Exception {
        planifier(admin, intervention.getId(), 0, technicienHabilite.getId()).andExpect(status().isOk());

        planifier(admin, intervention.getId(), 1, technicienHabilite.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSITION_INTERDITE"));
    }

    @Test
    @DisplayName("Une intervention ne peut pas être assignée à un utilisateur qui n'est pas technicien : 409")
    void assignationAUnNonTechnicien() throws Exception {
        planifier(admin, intervention.getId(), 0, demandeur.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TECHNICIEN_INVALIDE"));
    }

    @Test
    @DisplayName("Intervention inconnue : 404")
    void interventionInconnue() throws Exception {
        planifier(admin, 999_999L, 0, technicienHabilite.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTERVENTION_INTROUVABLE"));
    }
}