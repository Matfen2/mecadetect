package fr.mecadetect.technicien;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.intervention.Intervention;
import fr.mecadetect.intervention.InterventionRepository;
import fr.mecadetect.intervention.Priorite;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Liste des techniciens pour la planification : habilitations et charge (RG-09).
 * La base peut contenir d'autres comptes : chaque assertion cible un technicien par son id.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TechnicienApiTest {

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2026, 1, 5, 8, 0);

    @Autowired private WebApplicationContext context;
    @Autowired private InterventionRepository interventionRepository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;

    private MockMvc mockMvc;
    private TypeEquipement presses;
    private TypeEquipement tours;
    private Utilisateur admin;
    private Utilisateur demandeur;
    private Utilisateur lucas;
    private Utilisateur hugo;
    private Utilisateur inactif;
    private Intervention enCours;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        presses = typeRepository.saveAndFlush(new TypeEquipement("Presse de test"));
        tours = typeRepository.saveAndFlush(new TypeEquipement("Tour de test"));
        var presse = equipementRepository.saveAndFlush(
                new Equipement("TST-P1", "Presse 1", LocalDate.of(2020, 1, 6), presses, zone));

        admin = utilisateur("Martin", "Claire", "admin.tech@mecadetect.fr", Role.ADMIN);
        demandeur = utilisateur("Moreau", "Sophie", "demandeur.tech@mecadetect.fr", Role.DEMANDEUR);
        lucas = utilisateur("Bernard", "Lucas", "lucas.tech@mecadetect.fr", Role.TECHNICIEN);
        hugo = utilisateur("Petit", "Hugo", "hugo.tech@mecadetect.fr", Role.TECHNICIEN);
        inactif = new Utilisateur("Roux", "Paul", "paul.tech@mecadetect.fr", "hash", Role.TECHNICIEN);
        inactif.desactiver();
        inactif = utilisateurRepository.saveAndFlush(inactif);

        habiliter(lucas, presses);
        habiliter(lucas, tours);
        habiliter(hugo, tours);
        habiliter(inactif, presses);

        var planifiee = intervention(presse);
        planifiee.planifier(lucas, LUNDI_8H.plusDays(1), admin);
        interventionRepository.saveAndFlush(planifiee);

        enCours = intervention(presse);
        enCours.planifier(lucas, LUNDI_8H, admin);
        enCours.demarrer(lucas, LUNDI_8H);
        enCours = interventionRepository.saveAndFlush(enCours);
    }

    private Utilisateur utilisateur(String nom, String prenom, String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur(nom, prenom, email, "hash", role));
    }

    private void habiliter(Utilisateur technicien, TypeEquipement type) {
        habilitationRepository.saveAndFlush(new Habilitation(type, technicien, LocalDate.of(2025, 1, 6)));
    }

    private Intervention intervention(Equipement equipement) {
        return new Intervention(interventionRepository.prochainNumero(2026), "Fuite d'huile",
                "Fuite sur le vérin principal", Priorite.HAUTE, equipement, demandeur, LUNDI_8H);
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    private ResultActions lister(String url, Utilisateur utilisateur) throws Exception {
        return mockMvc.perform(get(url).with(connecte(utilisateur)));
    }

    private static String technicien(Utilisateur technicien, String champ) {
        return "$[?(@.idUtilisateur == %d)].%s".formatted(technicien.getId(), champ);
    }

    @Test
    @DisplayName("Chaque technicien actif est listé avec ses habilitations, sa charge et son intervention en cours")
    void listeAvecCharge() throws Exception {
        lister("/api/v1/techniciens", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath(technicien(lucas, "nom")).value("Bernard"))
                .andExpect(jsonPath(technicien(lucas, "habilitations[*].libelle"),
                        contains("Presse de test", "Tour de test")))
                .andExpect(jsonPath(technicien(lucas, "interventionsOuvertes")).value(2))
                .andExpect(jsonPath(technicien(lucas, "interventionEnCours.numero")).value(enCours.getNumero()))
                .andExpect(jsonPath(technicien(hugo, "interventionsOuvertes")).value(0));
    }

    @Test
    @DisplayName("Un technicien sans intervention en cours n'a pas de champ interventionEnCours")
    void sansInterventionEnCours() throws Exception {
        lister("/api/v1/techniciens", admin)
                .andExpect(jsonPath(technicien(hugo, "interventionEnCours.numero")).doesNotExist());
    }

    @Test
    @DisplayName("Filtre par type : seuls les techniciens habilités sur ce type (RG-09)")
    void filtreParType() throws Exception {
        lister("/api/v1/techniciens?idType=" + presses.getId(), admin)
                .andExpect(jsonPath(technicien(lucas, "nom")).value("Bernard"))
                .andExpect(jsonPath(technicien(hugo, "nom")).doesNotExist());
    }

    @Test
    @DisplayName("Les comptes désactivés et les autres rôles ne sont pas listés")
    void seulementLesTechniciensActifs() throws Exception {
        lister("/api/v1/techniciens", admin)
                .andExpect(jsonPath(technicien(inactif, "nom")).doesNotExist())
                .andExpect(jsonPath(technicien(admin, "nom")).doesNotExist());
    }

    @Test
    @DisplayName("La liste des techniciens est réservée à l'admin : 403")
    void reserveeAlAdmin() throws Exception {
        lister("/api/v1/techniciens", demandeur)
                .andExpect(status().isForbidden());
    }
}