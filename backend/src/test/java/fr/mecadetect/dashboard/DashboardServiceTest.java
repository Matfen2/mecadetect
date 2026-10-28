package fr.mecadetect.dashboard;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import fr.mecadetect.reporting.ChargeTechnicienPeriode;
import fr.mecadetect.reporting.EvolutionMensuelle;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tâche 5.1 : les indicateurs du tableau de bord, calculés sur les 12 derniers mois.
 * La base de test peut contenir d'autres données : chaque assertion compare le tableau de bord
 * avant et après la création des données du test, ou cible les éléments créés par le test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DashboardServiceTest {

    @Autowired private DashboardService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private TableauDeBord avant;
    private Equipement presse;
    private Utilisateur demandeur;
    private Utilisateur technicien;
    private LocalDateTime debutMois;
    private int numero;

    @BeforeEach
    void setUp() {
        avant = service.calculer();

        var zone = zoneRepository.saveAndFlush(new Zone("DB", "Zone du tableau de bord"));
        var presses = typeRepository.saveAndFlush(new TypeEquipement("Presse du tableau de bord"));
        presse = equipementRepository.saveAndFlush(
                new Equipement("DSH-P01", "Presse suivie", LocalDate.of(2020, 1, 6), presses, zone));
        demandeur = utilisateurRepository.saveAndFlush(
                new Utilisateur("Moreau", "Sophie", "demandeur.dsh@mecadetect.fr", "hash", Role.DEMANDEUR));
        technicien = utilisateurRepository.saveAndFlush(
                new Utilisateur("Bernard", "Lucas", "technicien.dsh@mecadetect.fr", "hash", Role.TECHNICIEN));
        debutMois = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();

        // Ce mois-ci : une demande critique, une intervention haute planifiée, une normale terminée en 4 h
        intervention(Priorite.CRITIQUE, "DEMANDEE");
        intervention(Priorite.HAUTE, "PLANIFIEE");
        intervention(Priorite.NORMALE, "TERMINEE");
        jdbc.update("UPDATE equipement SET statut = 'EN_PANNE' WHERE id_equipement = ?", presse.getId());
    }

    private void intervention(Priorite priorite, String statut) {
        String num = "INT-DSH-%06d".formatted(++numero);
        LocalDateTime demande = debutMois.plusHours(1);
        jdbc.update("""
                INSERT INTO intervention (numero, titre, description, priorite, statut, date_demande,
                                          id_equipement, id_demandeur, id_modifie_par)
                VALUES (?, 'Fuite', 'Fuite sur le vérin', ?, 'DEMANDEE', ?, ?, ?, ?)
                """, num, priorite.name(), Timestamp.valueOf(demande), presse.getId(), demandeur.getId(), demandeur.getId());
        switch (statut) {
            case "PLANIFIEE" -> jdbc.update("""
                    UPDATE intervention SET statut = 'PLANIFIEE', id_technicien = ?, date_planifiee = ?
                     WHERE numero = ?
                    """, technicien.getId(), Timestamp.valueOf(demande.plusHours(1)), num);
            case "TERMINEE" -> jdbc.update("""
                    UPDATE intervention
                       SET statut = 'TERMINEE', id_technicien = ?, date_planifiee = ?, date_debut = ?, date_fin = ?,
                           temps_passe_min = 90, compte_rendu = 'Joint remplacé', id_modifie_par = ?
                     WHERE numero = ?
                    """, technicien.getId(), Timestamp.valueOf(demande), Timestamp.valueOf(demande.plusHours(2)),
                    Timestamp.valueOf(demande.plusHours(4)), technicien.getId(), num);
            default -> { }
        }
    }

    @Test
    @DisplayName("Interventions ouvertes par priorité : les 4 priorités, de la plus urgente à la moins urgente")
    void ouvertesParPriorite() {
        var apres = service.calculer();

        assertThat(apres.ouvertesParPriorite()).containsOnlyKeys(Priorite.values());
        assertThat(apres.ouvertesParPriorite().keySet())
                .containsExactly(Priorite.CRITIQUE, Priorite.HAUTE, Priorite.NORMALE, Priorite.BASSE);
        assertThat(apres.ouvertesParPriorite().get(Priorite.CRITIQUE))
                .isEqualTo(avant.ouvertesParPriorite().get(Priorite.CRITIQUE) + 1);
        assertThat(apres.ouvertesParPriorite().get(Priorite.HAUTE))
                .isEqualTo(avant.ouvertesParPriorite().get(Priorite.HAUTE) + 1);
        // La terminée n'est plus ouverte
        assertThat(apres.ouvertesParPriorite().get(Priorite.NORMALE))
                .isEqualTo(avant.ouvertesParPriorite().get(Priorite.NORMALE));
    }

    @Test
    @DisplayName("Équipements en panne et temps moyen de résolution")
    void enPanneEtTempsMoyen() {
        var apres = service.calculer();

        assertThat(apres.equipementsEnPanne()).isEqualTo(avant.equipementsEnPanne() + 1);
        assertThat(apres.tempsMoyenResolutionHeures()).isPositive();
    }

    @Test
    @DisplayName("Évolution sur 12 mois consécutifs, le dernier étant le mois en cours")
    void evolutionMensuelle() {
        var apres = service.calculer();

        assertThat(apres.evolutionMensuelle()).hasSize(12);
        assertThat(apres.evolutionMensuelle().getLast().mois()).isEqualTo(YearMonth.now(clock));
        assertThat(apres.evolutionMensuelle().getFirst().mois()).isEqualTo(YearMonth.now(clock).minusMonths(11));
        assertThat(apres.evolutionMensuelle().getLast().nombre())
                .isEqualTo(avant.evolutionMensuelle().getLast().nombre() + 3);
    }

    @Test
    @DisplayName("Top des équipements : la presse apparaît avec ses 3 interventions")
    void topEquipements() {
        var apres = service.calculer();

        assertThat(apres.topEquipements())
                .filteredOn(top -> top.reference().equals("DSH-P01"))
                .singleElement()
                .satisfies(top -> assertThat(top.nombrePannes()).isEqualTo(3));
    }

    @Test
    @DisplayName("Charge des techniciens : interventions terminées et minutes passées ce mois-ci")
    void chargeTechniciens() {
        var apres = service.calculer();

        assertThat(apres.chargeTechniciens())
                .filteredOn(charge -> charge.idTechnicien() == technicien.getId())
                .singleElement()
                .satisfies(charge -> {
                    assertThat(charge.prenom()).isEqualTo("Lucas");
                    assertThat(charge.nom()).isEqualTo("Bernard");
                    assertThat(charge.terminees()).isEqualTo(1);
                    assertThat(charge.minutesPassees()).isEqualTo(90);
                });
    }
}