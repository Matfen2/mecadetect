package fr.mecadetect.intervention;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tâche 4.7 (UC-DB5) : une liste de 20 interventions, chacune avec un équipement et un technicien
 * différents. En chargeant les entités, l'affichage déclenche 1 + 20 + 20 = 41 requêtes ;
 * avec la projection en DTO, la page tient en 1 requête, plus 1 pour le comptage.
 * Statistiques Hibernate activées pour ce test uniquement.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
@Transactional
class InterventionRequetesSqlTest {

    private static final int NOMBRE = 20;
    private static final LocalDateTime DEBUT_2032 = LocalDateTime.of(2032, 1, 1, 0, 0);

    @Autowired private InterventionRepository interventionRepository;
    @Autowired private InterventionService service;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistiques;

    @BeforeEach
    void setUp() {
        var zone = zoneRepository.save(new Zone("N1", "Zone N+1"));
        var type = typeRepository.save(new TypeEquipement("Type N+1"));
        var admin = utilisateurRepository.save(new Utilisateur("Test", "Admin", "admin.n1@mecadetect.fr", "hash", Role.ADMIN));
        var demandeur = utilisateurRepository.save(
                new Utilisateur("Test", "Demandeur", "demandeur.n1@mecadetect.fr", "hash", Role.DEMANDEUR));

        // Le pire cas : un équipement et un technicien différents par intervention
        for (int n = 1; n <= NOMBRE; n++) {
            var equipement = equipementRepository.save(
                    new Equipement("N1-%02d".formatted(n), "Équipement " + n, LocalDate.of(2020, 1, 6), type, zone));
            var technicien = utilisateurRepository.save(new Utilisateur("Technicien" + n, "Test",
                    "technicien%02d.n1@mecadetect.fr".formatted(n), "hash", Role.TECHNICIEN));
            var intervention = new Intervention(interventionRepository.prochainNumero(2032), "Panne " + n,
                    "Panne numéro " + n, Priorite.NORMALE, equipement, demandeur, DEBUT_2032.plusDays(n));
            intervention.planifier(technicien, DEBUT_2032.plusDays(n + 1), admin);
            interventionRepository.save(intervention);
        }
        statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // Sans cela, les entités créées seraient servies depuis le contexte de persistance, sans requête
        entityManager.flush();
        entityManager.clear();
        statistiques.clear();
    }

    @Test
    @DisplayName("En chargeant les entités : 1 requête pour la page, puis 1 par équipement et 1 par technicien (41)")
    void demonstrationDuProblemeNPlusUn() {
        var page = interventionRepository.findAll(PageRequest.of(0, NOMBRE, Sort.by(Sort.Direction.DESC, "dateDemande")));
        // Ce qu'affiche une ligne de la liste : la référence de l'équipement et le nom du technicien
        page.getContent().forEach(intervention -> {
            intervention.getEquipement().getReference();
            intervention.getTechnicien().getNom();
        });

        assertThat(statistiques.getPrepareStatementCount()).isGreaterThanOrEqualTo(1 + 2L * NOMBRE);
    }

    @Test
    @DisplayName("Avec la recherche projetée en DTO : 1 requête pour la page, 1 pour le comptage")
    void rechercheSansNPlusUn() {
        var page = service.rechercher(
                new CriteresRechercheIntervention(null, null, null, LocalDate.of(2032, 1, 1), LocalDate.of(2032, 12, 31)),
                PaginationIntervention.versPageable(0, NOMBRE, null));

        assertThat(page.getContent()).hasSize(NOMBRE);
        assertThat(page.getContent()).allSatisfy(resume -> {
            assertThat(resume.equipementReference()).startsWith("N1-");
            assertThat(resume.technicienNomComplet()).isNotNull();
        });
        assertThat(statistiques.getPrepareStatementCount()).isEqualTo(2);
    }
}