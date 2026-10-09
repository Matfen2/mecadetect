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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jeu de données :
 *   PRS-001  Presse 250 tonnes  presse  atelier A  2020-03-15  EN_SERVICE
 *   PRS-002  Presse 100 tonnes  presse  atelier B  2018-06-01  EN_SERVICE
 *   TCN-001  Tour numérique     tour    atelier A  2022-09-10  EN_SERVICE
 *   TCN-002  Tour ancien        tour    atelier B  2010-01-01  ARCHIVE
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EquipementRechercheApiTest {

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
    private Zone atelierA;
    private TypeEquipement tour;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        atelierA = zoneRepository.save(new Zone("A1", "Atelier A"));
        var atelierB = zoneRepository.save(new Zone("B1", "Atelier B"));
        var presse = typeRepository.save(new TypeEquipement("Presse hydraulique"));
        tour = typeRepository.save(new TypeEquipement("Tour CN"));

        equipementRepository.save(new Equipement("PRS-001", "Presse 250 tonnes", LocalDate.of(2020, 3, 15), presse, atelierA));
        equipementRepository.save(new Equipement("PRS-002", "Presse 100 tonnes", LocalDate.of(2018, 6, 1), presse, atelierB));
        equipementRepository.save(new Equipement("TCN-001", "Tour numérique", LocalDate.of(2022, 9, 10), tour, atelierA));
        var archive = equipementRepository.save(new Equipement("TCN-002", "Tour ancien", LocalDate.of(2010, 1, 1), tour, atelierB));
        archive.archiver();
        equipementRepository.flush();
    }

    private ResultActions rechercher(String parametres) throws Exception {
        return mockMvc.perform(get(URL + parametres)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TECHNICIEN"))));
    }

    @Test
    @DisplayName("Sans filtre : les équipements archivés sont exclus, tri par référence")
    void sansFiltre() throws Exception {
        rechercher("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.taille").value(20))
                .andExpect(jsonPath("$.contenu[0].reference").value("PRS-001"))
                .andExpect(jsonPath("$.contenu[0].zone.code").value("A1"))
                .andExpect(jsonPath("$.contenu[0].type.libelle").value("Presse hydraulique"));
    }

    @Test
    @DisplayName("Filtre par statut ARCHIVE : seuls les équipements archivés")
    void filtreStatutArchive() throws Exception {
        rechercher("?statut=ARCHIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].reference").value("TCN-002"));
    }

    @Test
    @DisplayName("Filtre par zone")
    void filtreZone() throws Exception {
        rechercher("?idZone=" + atelierA.getId())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Filtre par type (les archivés restent exclus)")
    void filtreType() throws Exception {
        rechercher("?idType=" + tour.getId())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].reference").value("TCN-001"));
    }

    @Test
    @DisplayName("Recherche texte sur le nom, sans tenir compte de la casse")
    void rechercheSurLeNom() throws Exception {
        rechercher("?recherche=PRESSE")
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Recherche texte sur la référence")
    void rechercheSurLaReference() throws Exception {
        rechercher("?recherche=tcn")
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Les caractères spéciaux % et _ sont recherchés littéralement")
    void caracteresSpeciauxEchappes() throws Exception {
        rechercher("?recherche=%25")
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Pagination : page 2 avec 2 éléments par page")
    void pagination() throws Exception {
        rechercher("?page=1&size=2")
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.taille").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.contenu.length()").value(1));
    }

    @Test
    @DisplayName("Tri par date de mise en service décroissante")
    void triParDate() throws Exception {
        rechercher("?sort=dateMiseService,desc")
                .andExpect(jsonPath("$.contenu[0].reference").value("TCN-001"));
    }

    @Test
    @DisplayName("Tri par zone (champ d'une entité associée)")
    void triParZone() throws Exception {
        rechercher("?sort=zone,desc")
                .andExpect(jsonPath("$.contenu[0].zone.code").value("B1"));
    }

    @Test
    @DisplayName("Un champ de tri non autorisé : 400 TRI_INVALIDE")
    void triInvalide() throws Exception {
        rechercher("?sort=motDePasse,asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRI_INVALIDE"));
    }

    @Test
    @DisplayName("Une taille de page supérieure à 100 est refusée : 400")
    void tailleTropGrande() throws Exception {
        rechercher("?size=500")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un statut inconnu : 400 PARAMETRE_INVALIDE")
    void statutInconnu() throws Exception {
        rechercher("?statut=CASSE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
    }

    @Test
    @DisplayName("La recherche exige une authentification : 401")
    void sansAuthentification() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}