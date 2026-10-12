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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tâche 3.3 : déclaration de panne (POST /interventions), RG-06 et RG-07. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InterventionDeclarationApiTest {

    private static final String URL = "/api/v1/interventions";

    @Autowired private WebApplicationContext context;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private MockMvc mockMvc;
    private Equipement presse;
    private Utilisateur demandeur;
    private Utilisateur admin;
    private Utilisateur technicien;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        var type = typeRepository.saveAndFlush(new TypeEquipement("Type de test"));
        presse = equipementRepository.saveAndFlush(
                new Equipement("TST-001", "Presse de test", LocalDate.of(2020, 1, 6), type, zone));
        demandeur = utilisateur("demandeur.decl@mecadetect.fr", Role.DEMANDEUR);
        admin = utilisateur("admin.decl@mecadetect.fr", Role.ADMIN);
        technicien = utilisateur("technicien.decl@mecadetect.fr", Role.TECHNICIEN);
    }

    private Utilisateur utilisateur(String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur("Test", "Utilisateur", email, "hash", role));
    }

    /** Simule un jeton : le sub porte l'identifiant, comme le vrai JwtService. */
    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    private static String declaration(Long idEquipement) {
        return """
                {"idEquipement": %d, "titre": "Fuite d'huile", "description": "Fuite importante sur le vérin principal", "priorite": "HAUTE"}
                """.formatted(idEquipement);
    }

    @Test
    @DisplayName("Un demandeur déclare une panne : 201, intervention DEMANDEE et équipement EN_PANNE (RG-06)")
    void declarationParDemandeur() throws Exception {
        mockMvc.perform(post(URL).with(connecte(demandeur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declaration(presse.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idIntervention").isNumber())
                .andExpect(jsonPath("$.numero").value(matchesPattern("INT-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.statut").value("DEMANDEE"))
                .andExpect(jsonPath("$.priorite").value("HAUTE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.equipement.reference").value("TST-001"))
                .andExpect(jsonPath("$.equipement.statut").value("EN_PANNE"))
                .andExpect(jsonPath("$.demandeur.idUtilisateur").value(demandeur.getId().intValue()))
                .andExpect(jsonPath("$.technicien").doesNotExist())
                .andExpect(jsonPath("$.historique").isArray());

        assertThat(equipementRepository.findById(presse.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutEquipement.EN_PANNE);
    }

    @Test
    @DisplayName("Un admin peut aussi déclarer une panne")
    void declarationParAdmin() throws Exception {
        mockMvc.perform(post(URL).with(connecte(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declaration(presse.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.demandeur.idUtilisateur").value(admin.getId().intValue()));
    }

    @Test
    @DisplayName("Un technicien ne déclare pas de panne : 403")
    void declarationParTechnicien() throws Exception {
        mockMvc.perform(post(URL).with(connecte(technicien))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declaration(presse.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sans authentification : 401")
    void sansAuthentification() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declaration(presse.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RG-07 : pas de panne sur un équipement archivé : 409")
    void equipementArchive() throws Exception {
        presse.archiver();
        equipementRepository.saveAndFlush(presse);

        mockMvc.perform(post(URL).with(connecte(demandeur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declaration(presse.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPEMENT_ARCHIVE"));
    }

    @Test
    @DisplayName("Équipement inconnu : 404")
    void equipementInconnu() throws Exception {
        mockMvc.perform(post(URL).with(connecte(demandeur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declaration(999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPEMENT_INTROUVABLE"));
    }

    @Test
    @DisplayName("Titre trop court et description absente : 400 avec le détail des champs")
    void donneesInvalides() throws Exception {
        mockMvc.perform(post(URL).with(connecte(demandeur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idEquipement": %d, "titre": "Ab", "priorite": "HAUTE"}
                                """.formatted(presse.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DONNEES_INVALIDES"))
                .andExpect(jsonPath("$.erreurs.length()").value(2));
    }

    @Test
    @DisplayName("Deux pannes sur le même équipement : deux numéros différents, l'équipement reste EN_PANNE")
    void deuxDeclarations() throws Exception {
        var premiere = mockMvc.perform(post(URL).with(connecte(demandeur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declaration(presse.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var seconde = mockMvc.perform(post(URL).with(connecte(demandeur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declaration(presse.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipement.statut").value("EN_PANNE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(numero(seconde)).isNotEqualTo(numero(premiere));
    }

    private static String numero(String json) {
        return json.replaceAll(".*\"numero\":\"([^\"]+)\".*", "$1");
    }
}