package fr.mecadetect.equipement;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mesure le nombre de requêtes SQL exécutées pour afficher une liste d'équipements
 * avec leur zone et leur type. Statistiques Hibernate activées pour ce test uniquement.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
@Transactional
class EquipementRequetesSqlTest {

    private static final int NOMBRE_EQUIPEMENTS = 5;

    @Autowired
    private EquipementService service;

    @Autowired
    private EquipementRepository equipementRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private TypeEquipementRepository typeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistiques;

    @BeforeEach
    void setUp() {
        // Une zone et un type différents par équipement : le pire cas pour le N+1
        for (int i = 1; i <= NOMBRE_EQUIPEMENTS; i++) {
            var zone = zoneRepository.save(new Zone("Z" + i, "Zone " + i));
            var type = typeRepository.save(new TypeEquipement("Type " + i));
            equipementRepository.save(new Equipement("EQ-00" + i, "Équipement " + i, LocalDate.of(2020, 1, i), type, zone));
        }
        statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        viderLeContexteDePersistance();
    }

    /** Sans cela, les entités créées plus haut seraient servies depuis le cache, sans requête. */
    private void viderLeContexteDePersistance() {
        entityManager.flush();
        entityManager.clear();
        statistiques.clear();
    }

    @Test
    @DisplayName("Sans EntityGraph : 1 requête pour la liste, puis 1 par zone et 1 par type (problème N+1)")
    void demonstrationDuProblemeNPlusUn() {
        var page = equipementRepository.findAll(PageRequest.of(0, 20));
        page.getContent().forEach(EquipementMapper::versDto); // accède à zone et type

        // 1 requête + 5 zones + 5 types = 11 au minimum
        assertThat(statistiques.getPrepareStatementCount()).isGreaterThanOrEqualTo(1 + 2L * NOMBRE_EQUIPEMENTS);
    }

    @Test
    @DisplayName("Avec la recherche du service : zone et type chargés par jointure, 2 requêtes au plus")
    void rechercheSansNPlusUn() {
        var criteres = new CriteresRechercheEquipement(null, null, null, null);

        var page = service.rechercher(criteres, PageRequest.of(0, 20, Sort.by("reference")));
        page.getContent().forEach(EquipementMapper::versDto);

        assertThat(page.getContent()).hasSize(NOMBRE_EQUIPEMENTS);
        // Liste avec jointures + éventuel comptage pour la pagination
        assertThat(statistiques.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }
}