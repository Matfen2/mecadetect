package fr.mecadetect.intervention;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.intervention.domain.Intervention;
import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.intervention.infrastructure.InterventionJpaRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consultation : détail, recherche paginée et filtrée, « Mes interventions »
 * et historique d'un équipement. Jeu de données : 5 interventions dans 5 statuts différents.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InterventionConsultationApiTest {

    @Autowired private WebApplicationContext context;
    @Autowired private InterventionJpaRepository interventionRepository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private MockMvc mockMvc;
    private Equipement equipementA;
    private Utilisateur admin;
    private Utilisateur demandeur;
    private Utilisateur technicien;
    private Utilisateur autreTechnicien;

    private Intervention demandee;    // A, HAUTE, 1er mars
    private Intervention planifiee;   // A, NORMALE, 5 mars, technicien
    private Intervention enCours;     // B, CRITIQUE, 8 mars, technicien

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        var type = typeRepository.saveAndFlush(new TypeEquipement("Type de test"));
        equipementA = equipementRepository.saveAndFlush(
                new Equipement("TST-A", "Presse A", LocalDate.of(2020, 1, 6), type, zone));
        var equipementB = equipementRepository.saveAndFlush(
                new Equipement("TST-B", "Tour B", LocalDate.of(2021, 2, 1), type, zone));

        admin = utilisateur("Martin", "Claire", "admin.consult@mecadetect.fr", Role.ADMIN);
        demandeur = utilisateur("Moreau", "Sophie", "demandeur.consult@mecadetect.fr", Role.DEMANDEUR);
        technicien = utilisateur("Bernard", "Lucas", "technicien.consult@mecadetect.fr", Role.TECHNICIEN);
        autreTechnicien = utilisateur("Petit", "Hugo", "autre.consult@mecadetect.fr", Role.TECHNICIEN);

        demandee = enregistrer(intervention(equipementA, Priorite.HAUTE, jour(3, 1)));

        planifiee = intervention(equipementA, Priorite.NORMALE, jour(3, 5));
        planifiee.planifier(technicien, jour(3, 10), admin);
        planifiee = enregistrer(planifiee);

        enCours = intervention(equipementB, Priorite.CRITIQUE, jour(3, 8));
        enCours.planifier(technicien, jour(3, 9), admin);
        enCours.demarrer(technicien, jour(3, 9));
        enCours = enregistrer(enCours);

        var terminee = intervention(equipementB, Priorite.BASSE, jour(2, 10));
        terminee.planifier(autreTechnicien, jour(2, 11), admin);
        terminee.demarrer(autreTechnicien, jour(2, 11));
        terminee.cloturer("Courroie remplacée, essai concluant", 45, autreTechnicien, jour(2, 11).plusHours(1));
        enregistrer(terminee);

        var annulee = intervention(equipementA, Priorite.NORMALE, jour(1, 20));
        annulee.annuler("Doublon de la demande précédente", admin);
        enregistrer(annulee);
    }

    private static LocalDateTime jour(int mois, int jour) {
        return LocalDateTime.of(2026, mois, jour, 8, 0);
    }

    private Utilisateur utilisateur(String nom, String prenom, String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur(nom, prenom, email, "hash", role));
    }

    private Intervention intervention(Equipement equipement, Priorite priorite, LocalDateTime dateDemande) {
        return new Intervention(interventionRepository.prochainNumero(2026), "Panne " + priorite,
                "Description de la panne de test", priorite, equipement, demandeur, dateDemande);
    }

    private Intervention enregistrer(Intervention intervention) {
        return interventionRepository.saveAndFlush(intervention);
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    private ResultActions lire(String url, Utilisateur utilisateur) throws Exception {
        return mockMvc.perform(get(url).with(connecte(utilisateur)));
    }

    // ------------------------------------------------------------------ détail

    @Test
    @DisplayName("Le détail d'une intervention est consultable par tout utilisateur connecté")
    void detail() throws Exception {
        lire("/api/v1/interventions/" + enCours.getId(), demandeur)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(enCours.getNumero()))
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.equipement.reference").value("TST-B"))
                .andExpect(jsonPath("$.technicien.nom").value("Bernard"))
                .andExpect(jsonPath("$.historique").isArray());
    }

    @Test
    @DisplayName("Détail d'une intervention inconnue : 404")
    void detailInconnu() throws Exception {
        lire("/api/v1/interventions/999999", demandeur)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTERVENTION_INTROUVABLE"));
    }

    // ------------------------------------------------------------------ recherche

    @Test
    @DisplayName("Sans filtre : toutes les interventions, les demandes les plus récentes d'abord")
    void rechercheSansFiltre() throws Exception {
        lire("/api/v1/interventions", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.contenu[0].numero").value(enCours.getNumero()))
                .andExpect(jsonPath("$.contenu[0].equipementReference").value("TST-B"))
                .andExpect(jsonPath("$.contenu[0].technicienNomComplet").value("Lucas Bernard"))
                .andExpect(jsonPath("$.contenu[2].technicienNomComplet").doesNotExist());
    }

    @Test
    @DisplayName("Filtre par statut")
    void filtreStatut() throws Exception {
        lire("/api/v1/interventions?statut=PLANIFIEE", admin)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].numero").value(planifiee.getNumero()));
    }

    @Test
    @DisplayName("Filtre par technicien assigné")
    void filtreTechnicien() throws Exception {
        lire("/api/v1/interventions?idTechnicien=" + technicien.getId(), admin)
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Filtre par priorité")
    void filtrePriorite() throws Exception {
        lire("/api/v1/interventions?priorite=CRITIQUE", admin)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].numero").value(enCours.getNumero()));
    }

    @Test
    @DisplayName("Filtre par période de demande, bornes incluses")
    void filtrePeriode() throws Exception {
        lire("/api/v1/interventions?depuis=2026-03-01&jusqua=2026-03-05", admin)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.contenu[0].numero").value(planifiee.getNumero()))
                .andExpect(jsonPath("$.contenu[1].numero").value(demandee.getNumero()));
    }

    @Test
    @DisplayName("Tri par numéro croissant")
    void triParNumero() throws Exception {
        lire("/api/v1/interventions?sort=numero,asc", admin)
                .andExpect(jsonPath("$.contenu[0].numero").value(demandee.getNumero()));
    }

    @Test
    @DisplayName("Un champ de tri non autorisé : 400 TRI_INVALIDE")
    void triInvalide() throws Exception {
        lire("/api/v1/interventions?sort=titre,asc", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRI_INVALIDE"));
    }

    // ------------------------------------------------------------------ interventions d'un technicien

    private String interventionsDe(Utilisateur technicienConsulte) {
        return "/api/v1/techniciens/" + technicienConsulte.getId() + "/interventions";
    }

    @Test
    @DisplayName("Un technicien voit ses interventions planifiées et en cours, celle en cours d'abord")
    void interventionsDuTechnicien() throws Exception {
        lire(interventionsDe(technicien), technicien)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.contenu[0].statut").value("EN_COURS"))
                .andExpect(jsonPath("$.contenu[1].statut").value("PLANIFIEE"));
    }

    @Test
    @DisplayName("Un technicien ne peut pas consulter les interventions d'un autre : 403")
    void interventionsDUnAutreTechnicien() throws Exception {
        lire(interventionsDe(autreTechnicien), technicien)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTERVENTIONS_D_UN_AUTRE_TECHNICIEN"));
    }

    @Test
    @DisplayName("L'admin peut consulter les interventions de n'importe quel technicien")
    void interventionsDUnTechnicienParAdmin() throws Exception {
        lire(interventionsDe(technicien), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Un demandeur ne peut pas consulter les interventions d'un technicien : 403")
    void interventionsDUnTechnicienParDemandeur() throws Exception {
        lire(interventionsDe(technicien), demandeur)
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ historique d'un équipement

    @Test
    @DisplayName("Historique d'un équipement : ses interventions, les plus récentes d'abord")
    void historiqueEquipement() throws Exception {
        lire("/api/v1/equipements/" + equipementA.getId() + "/interventions", demandeur)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.contenu[0].numero").value(planifiee.getNumero()));
    }

    @Test
    @DisplayName("Historique d'un équipement inconnu : 404")
    void historiqueEquipementInconnu() throws Exception {
        lire("/api/v1/equipements/999999/interventions", demandeur)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPEMENT_INTROUVABLE"));
    }
}