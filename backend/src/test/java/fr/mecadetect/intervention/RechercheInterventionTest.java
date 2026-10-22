package fr.mecadetect.intervention;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.intervention.domain.Intervention;
import fr.mecadetect.intervention.domain.InterventionResume;
import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.intervention.domain.StatutIntervention;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tâche 4.4 : la recherche construite dynamiquement donne les mêmes résultats que l'ancienne
 * requête, pour chaque filtre seul et combiné. Les dates sont en 2031 : la période filtrée
 * ne contient que les interventions du test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RechercheInterventionTest {

    private static final LocalDateTime DEBUT_2031 = LocalDateTime.of(2031, 1, 1, 0, 0);
    private static final LocalDateTime FIN_2031 = LocalDateTime.of(2032, 1, 1, 0, 0);
    private static final Pageable PLUS_RECENTES_D_ABORD =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "dateDemande").and(Sort.by("id")));

    @Autowired private InterventionJpaRepository repository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private Equipement presse;
    private Utilisateur hugo;
    private Intervention urgentePlanifiee;
    private Intervention normaleDemandee;
    private Intervention ancienneSurLeTour;

    @BeforeEach
    void setUp() {
        var zone = zoneRepository.saveAndFlush(new Zone("S1", "Zone de recherche"));
        var type = typeRepository.saveAndFlush(new TypeEquipement("Type de recherche"));
        presse = equipementRepository.saveAndFlush(new Equipement("SRCH-P", "Presse", LocalDate.of(2020, 1, 6), type, zone));
        var tour = equipementRepository.saveAndFlush(new Equipement("SRCH-T", "Tour", LocalDate.of(2020, 1, 6), type, zone));
        var admin = utilisateur("admin.srch@mecadetect.fr", Role.ADMIN);
        var demandeur = utilisateur("demandeur.srch@mecadetect.fr", Role.DEMANDEUR);
        hugo = utilisateur("hugo.srch@mecadetect.fr", Role.TECHNICIEN);

        urgentePlanifiee = new Intervention(repository.prochainNumero(2031), "Fuite", "Fuite sur le vérin",
                Priorite.CRITIQUE, presse, demandeur, LocalDateTime.of(2031, 3, 10, 8, 0));
        urgentePlanifiee.planifier(hugo, LocalDateTime.of(2031, 3, 10, 10, 0), admin);
        urgentePlanifiee = repository.saveAndFlush(urgentePlanifiee);

        normaleDemandee = repository.saveAndFlush(new Intervention(repository.prochainNumero(2031), "Bruit",
                "Bruit anormal du moteur", Priorite.NORMALE, presse, demandeur, LocalDateTime.of(2031, 3, 5, 8, 0)));

        ancienneSurLeTour = repository.saveAndFlush(new Intervention(repository.prochainNumero(2031), "Vibrations",
                "Vibrations à haute vitesse", Priorite.NORMALE, tour, demandeur, LocalDateTime.of(2031, 1, 20, 8, 0)));
    }

    private Utilisateur utilisateur(String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur("Test", "Recherche", email, "hash", role));
    }

    /** Recherche limitée à 2031, avec les filtres donnés. */
    private java.util.List<Long> rechercher(StatutIntervention statut, Priorite priorite, Long idTechnicien,
                                            Long idEquipement) {
        return repository.rechercher(statut, priorite, idTechnicien, idEquipement, DEBUT_2031, FIN_2031,
                        PLUS_RECENTES_D_ABORD)
                .map(InterventionResume::idIntervention)
                .getContent();
    }

    @Test
    @DisplayName("Sans autre filtre que la période : toutes, les plus récentes d'abord, comptage exact")
    void periodeSeule() {
        var page = repository.rechercher(null, null, null, null, DEBUT_2031, FIN_2031, PLUS_RECENTES_D_ABORD);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(InterventionResume::idIntervention)
                .containsExactly(urgentePlanifiee.getId(), normaleDemandee.getId(), ancienneSurLeTour.getId());
    }

    @Test
    @DisplayName("Filtre statut seul")
    void statut() {
        assertThat(rechercher(StatutIntervention.DEMANDEE, null, null, null))
                .containsExactly(normaleDemandee.getId(), ancienneSurLeTour.getId());
    }

    @Test
    @DisplayName("Filtre priorité seul")
    void priorite() {
        assertThat(rechercher(null, Priorite.CRITIQUE, null, null)).containsExactly(urgentePlanifiee.getId());
    }

    @Test
    @DisplayName("Filtre technicien seul, lu sur la clé étrangère")
    void technicien() {
        assertThat(rechercher(null, null, hugo.getId(), null)).containsExactly(urgentePlanifiee.getId());
    }

    @Test
    @DisplayName("Filtres combinés : équipement et statut")
    void filtresCombines() {
        assertThat(rechercher(StatutIntervention.DEMANDEE, null, null, presse.getId()))
                .containsExactly(normaleDemandee.getId());
    }

    @Test
    @DisplayName("Résumé complet : équipement et technicien projetés, technicien absent laissé à null")
    void projection() {
        var page = repository.rechercher(null, null, null, presse.getId(), DEBUT_2031, FIN_2031, PLUS_RECENTES_D_ABORD);

        assertThat(page.getContent()).first().satisfies(resume -> {
            assertThat(resume.equipementReference()).isEqualTo("SRCH-P");
            assertThat(resume.technicienNomComplet()).isEqualTo("Recherche Test");
            assertThat(resume.idTechnicien()).isEqualTo(hugo.getId());
        });
        assertThat(page.getContent()).last().satisfies(resume -> assertThat(resume.technicienNomComplet()).isNull());
    }

    @Test
    @DisplayName("Pagination : la deuxième page et le comptage total sont cohérents")
    void pagination() {
        var page = repository.rechercher(null, null, null, null, DEBUT_2031, FIN_2031,
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "dateDemande")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(InterventionResume::idIntervention)
                .containsExactly(ancienneSurLeTour.getId());
    }
}