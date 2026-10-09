package fr.mecadetect.equipement;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de l'API des équipements. L'utilisateur connecté est simulé avec jwt() de
 * spring-security-test : pas besoin de passer par le login pour chaque test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EquipementApiTest {

    private static final String URL = "/api/v1/equipements";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EquipementRepository equipementRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private TypeEquipementRepository typeRepository;

    private MockMvc mockMvc;
    private Zone zone;
    private TypeEquipement type;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        type = typeRepository.saveAndFlush(new TypeEquipement("Type de test"));
    }

    private static RequestPostProcessor connecteEn(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String corps(String reference, String nom, Long idType, Long idZone) {
        return """
                {"reference": "%s", "nom": "%s", "dateMiseService": "2022-01-10", "idType": %d, "idZone": %d}
                """.formatted(reference, nom, idType, idZone);
    }

    private Equipement equipementExistant(String reference) {
        return equipementRepository.saveAndFlush(
                new Equipement(reference, "Équipement existant", LocalDate.of(2021, 5, 1), type, zone));
    }

    // ---------------------------------------------------------------
    // Création
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Un admin crée un équipement : 201 avec l'équipement, sa zone et son type")
    void creationParAdmin() throws Exception {
        mockMvc.perform(post(URL).with(connecteEn("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps("PRS-100", "Presse 250 tonnes", type.getId(), zone.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEquipement").isNumber())
                .andExpect(jsonPath("$.reference").value("PRS-100"))
                .andExpect(jsonPath("$.statut").value("EN_SERVICE"))
                .andExpect(jsonPath("$.zone.code").value("T1"))
                .andExpect(jsonPath("$.type.libelle").value("Type de test"));
    }

    @Test
    @DisplayName("Sans authentification : 401")
    void creationSansAuthentification() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps("PRS-101", "Presse", type.getId(), zone.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un demandeur ne peut pas créer d'équipement : 403")
    void creationParDemandeurInterdite() throws Exception {
        mockMvc.perform(post(URL).with(connecteEn("DEMANDEUR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps("PRS-102", "Presse", type.getId(), zone.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Une référence déjà utilisée : 409 REFERENCE_DEJA_UTILISEE")
    void referenceDejaUtilisee() throws Exception {
        equipementExistant("PRS-103");

        mockMvc.perform(post(URL).with(connecteEn("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps("prs-103", "Autre presse", type.getId(), zone.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFERENCE_DEJA_UTILISEE"));
    }

    @Test
    @DisplayName("Une zone inexistante : 400 ZONE_INCONNUE")
    void zoneInconnue() throws Exception {
        mockMvc.perform(post(URL).with(connecteEn("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps("PRS-104", "Presse", type.getId(), 999_999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ZONE_INCONNUE"));
    }

    @Test
    @DisplayName("Un nom vide : 400 DONNEES_INVALIDES avec le champ en erreur")
    void donneesInvalides() throws Exception {
        mockMvc.perform(post(URL).with(connecteEn("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps("PRS-105", "", type.getId(), zone.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DONNEES_INVALIDES"))
                .andExpect(jsonPath("$.erreurs[0].champ").value("nom"));
    }

    @Test
    @DisplayName("Un JSON mal formé : 400 REQUETE_ILLISIBLE")
    void jsonMalForme() throws Exception {
        mockMvc.perform(post(URL).with(connecteEn("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ ceci n'est pas du JSON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_ILLISIBLE"));
    }

    // ---------------------------------------------------------------
    // Consultation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Un technicien consulte la fiche d'un équipement : 200")
    void consultation() throws Exception {
        var equipement = equipementExistant("PRS-106");

        mockMvc.perform(get(URL + "/{id}", equipement.getId()).with(connecteEn("TECHNICIEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("PRS-106"))
                .andExpect(jsonPath("$.zone.libelle").value("Zone de test"));
    }

    @Test
    @DisplayName("Un équipement inexistant : 404 EQUIPEMENT_INTROUVABLE")
    void equipementIntrouvable() throws Exception {
        mockMvc.perform(get(URL + "/{id}", 999_999L).with(connecteEn("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPEMENT_INTROUVABLE"));
    }

    @Test
    @DisplayName("Un identifiant non numérique : 400 PARAMETRE_INVALIDE")
    void identifiantNonNumerique() throws Exception {
        mockMvc.perform(get(URL + "/abc").with(connecteEn("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
    }

    // ---------------------------------------------------------------
    // Modification et archivage
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Un admin modifie un équipement : 200 avec les nouvelles valeurs")
    void modification() throws Exception {
        var equipement = equipementExistant("PRS-107");

        mockMvc.perform(put(URL + "/{id}", equipement.getId()).with(connecteEn("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps("PRS-107", "Presse rénovée", type.getId(), zone.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Presse rénovée"));
    }

    @Test
    @DisplayName("Modifier la référence vers une référence déjà prise : 409")
    void modificationVersReferenceExistante() throws Exception {
        equipementExistant("PRS-108");
        var autre = equipementExistant("PRS-109");

        mockMvc.perform(put(URL + "/{id}", autre.getId()).with(connecteEn("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps("PRS-108", "Doublon", type.getId(), zone.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFERENCE_DEJA_UTILISEE"));
    }

    @Test
    @DisplayName("Archiver : 204, puis l'équipement apparaît au statut ARCHIVE")
    void archivage() throws Exception {
        var equipement = equipementExistant("PRS-110");

        mockMvc.perform(delete(URL + "/{id}", equipement.getId()).with(connecteEn("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(URL + "/{id}", equipement.getId()).with(connecteEn("ADMIN")))
                .andExpect(jsonPath("$.statut").value("ARCHIVE"));
    }

    @Test
    @DisplayName("Archiver deux fois : 409 EQUIPEMENT_DEJA_ARCHIVE")
    void doubleArchivage() throws Exception {
        var equipement = equipementExistant("PRS-111");
        mockMvc.perform(delete(URL + "/{id}", equipement.getId()).with(connecteEn("ADMIN")));

        mockMvc.perform(delete(URL + "/{id}", equipement.getId()).with(connecteEn("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPEMENT_DEJA_ARCHIVE"));
    }

    @Test
    @DisplayName("Modifier un équipement archivé : 409 EQUIPEMENT_ARCHIVE")
    void modificationArchive() throws Exception {
        var equipement = equipementExistant("PRS-112");
        mockMvc.perform(delete(URL + "/{id}", equipement.getId()).with(connecteEn("ADMIN")));

        mockMvc.perform(put(URL + "/{id}", equipement.getId()).with(connecteEn("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps("PRS-112", "Modif", type.getId(), zone.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPEMENT_ARCHIVE"));
    }

    @Test
    @DisplayName("Un technicien ne peut pas archiver : 403")
    void archivageParTechnicienInterdit() throws Exception {
        var equipement = equipementExistant("PRS-113");

        mockMvc.perform(delete(URL + "/{id}", equipement.getId()).with(connecteEn("TECHNICIEN")))
                .andExpect(status().isForbidden());
    }
}