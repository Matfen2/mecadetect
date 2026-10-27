package fr.mecadetect.equipement.importation;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// POST /api/v1/equipements/imports : import du parc depuis un fichier CSV, par le job Spring Batch.
///
/// Pas de `@Transactional` sur la classe : le job valide ses propres lots, dans ses propres
/// transactions. Les données du test sont donc supprimées à la main après chaque test.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ImportEquipementsApiTest {

    private static final String ENTETE = "reference;nom;date_mise_service;type;zone\n";

    /// Ligne 1 : en-tête. Lignes 2, 3 et 7 valides ; 4, 5 et 6 rejetées.
    private static final String FICHIER = ENTETE + """
            IMP-001;Presse 200 t;2022-01-10;Presse import;IMP
            IMP-002;"Tour CN; 3 axes";10/03/2021;presse IMPORT;imp
            IMP-003;Compresseur;2022-01-10;Presse import;XXX
            imp-001;Presse en double;2022-01-10;Presse import;IMP
            IMP-004;Ligne incomplète
            IMP-005;Convoyeur;2020-05-05;Presse import;IMP
            """;

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private MockMvc mockMvc;
    private Utilisateur admin;
    private Utilisateur technicien;

    @BeforeEach
    void setUp() {
        nettoyer();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        zoneRepository.saveAndFlush(new Zone("IMP", "Zone d'import"));
        typeRepository.saveAndFlush(new TypeEquipement("Presse import"));
        admin = utilisateurRepository.saveAndFlush(
                new Utilisateur("Martin", "Claire", "admin.import@mecadetect.fr", "hash", Role.ADMIN));
        technicien = utilisateurRepository.saveAndFlush(
                new Utilisateur("Petit", "Hugo", "technicien.import@mecadetect.fr", "hash", Role.TECHNICIEN));
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM equipement WHERE UPPER(reference) LIKE 'IMP-%'");
        jdbc.update("DELETE FROM type_equipement WHERE libelle = 'Presse import'");
        jdbc.update("DELETE FROM zone WHERE code = 'IMP'");
        jdbc.update("DELETE FROM utilisateur WHERE email IN ('admin.import@mecadetect.fr', 'technicien.import@mecadetect.fr')");
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    private ResultActions importer(String contenu, Utilisateur utilisateur) throws Exception {
        var fichier = new MockMultipartFile(
                "fichier", "parc.csv", "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/equipements/imports").file(fichier).with(connecte(utilisateur)));
    }

    @Test
    @DisplayName("L'admin importe un fichier : les lignes valides sont créées, les autres rejetées avec leur motif")
    void importAvecRejets() throws Exception {
        importer(FICHIER, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignesLues").value(6))
                .andExpect(jsonPath("$.importes").value(3))
                .andExpect(jsonPath("$.rejetes").value(3))
                .andExpect(jsonPath("$.rejets.length()").value(3))
                .andExpect(jsonPath("$.rejets[0].ligne").value(4))
                .andExpect(jsonPath("$.rejets[0].motif").value("La zone « XXX » n'existe pas."))
                .andExpect(jsonPath("$.rejets[1].ligne").value(5))
                .andExpect(jsonPath("$.rejets[1].motif")
                        .value("La référence « imp-001 » apparaît déjà à la ligne 2 du fichier."))
                .andExpect(jsonPath("$.rejets[2].ligne").value(6));

        assertThat(equipementRepository.existsByReferenceIgnoreCase("IMP-001")).isTrue();
        assertThat(equipementRepository.existsByReferenceIgnoreCase("IMP-002")).isTrue();
        assertThat(equipementRepository.existsByReferenceIgnoreCase("IMP-005")).isTrue();
        assertThat(equipementRepository.existsByReferenceIgnoreCase("IMP-003")).isFalse();
    }

    @Test
    @DisplayName("Réimporter le même fichier ne crée aucun doublon : les références existent déjà")
    void reimportSansDoublon() throws Exception {
        importer(FICHIER, admin).andExpect(status().isOk());

        importer(FICHIER, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importes").value(0))
                .andExpect(jsonPath("$.rejetes").value(6));
    }

    @Test
    @DisplayName("L'import est réservé à l'admin : 403")
    void reserveAlAdmin() throws Exception {
        importer(FICHIER, technicien).andExpect(status().isForbidden());

        assertThat(equipementRepository.existsByReferenceIgnoreCase("IMP-001")).isFalse();
    }

    @Test
    @DisplayName("Un fichier sans aucune ligne de données est refusé : 400")
    void fichierVide() throws Exception {
        importer(ENTETE, admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FICHIER_VIDE"));
    }
}
