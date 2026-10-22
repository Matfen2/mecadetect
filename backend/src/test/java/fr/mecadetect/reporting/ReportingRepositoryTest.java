package fr.mecadetect.reporting;

import fr.mecadetect.TestPersistanceOracle;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.intervention.domain.Priorite;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// Tâche 4.3 (UC-DB2) : requêtes de reporting sur un jeu de données dont on connaît le résultat.
///
/// Toutes les dates sont en 2030 (et 2028) : aucune autre donnée de la base de test ne tombe dans ces périodes.
///
/// | Équipement | Demandes (2030)                    | Remarque                            |
/// |------------|------------------------------------|-------------------------------------|
/// | RPT-A      | février, mars, mai (terminées)     | 3 pannes                            |
/// | RPT-B      | mars, mars, juin (terminées)       | 3 pannes : ex aequo avec A          |
/// | RPT-C      | mai (terminée), mai (annulée)      | 1 panne : l'annulée ne compte pas   |
/// | RPT-D      | 2028 seulement                     | hors période                        |
@TestPersistanceOracle
class ReportingRepositoryTest {

    private static final LocalDateTime DEBUT_2030 = LocalDateTime.of(2030, 1, 1, 0, 0);
    private static final LocalDateTime JUILLET_2030 = LocalDateTime.of(2030, 7, 1, 0, 0);

    @Autowired private ReportingRepository reporting;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private Utilisateur demandeur;
    private Utilisateur hugo;
    private Utilisateur lea;
    private int numero;

    @BeforeEach
    void setUp() {
        var zone = zoneRepository.saveAndFlush(new Zone("R1", "Zone de reporting"));
        var type = typeRepository.saveAndFlush(new TypeEquipement("Type de reporting"));
        var a = equipement("RPT-A", "Presse A", type, zone);
        var b = equipement("RPT-B", "Tour B", type, zone);
        var c = equipement("RPT-C", "Robot C", type, zone);
        var d = equipement("RPT-D", "Convoyeur D", type, zone);

        demandeur = utilisateur("Moreau", "Sophie", "demandeur.rpt@mecadetect.fr", Role.DEMANDEUR);
        hugo = utilisateur("Petit", "Hugo", "hugo.rpt@mecadetect.fr", Role.TECHNICIEN);
        lea = utilisateur("Roux", "Léa", "lea.rpt@mecadetect.fr", Role.TECHNICIEN);

        // Terminées : équipement, demande, durée totale en heures, priorité, technicien, minutes de travail
        terminee(a, LocalDateTime.of(2030, 2, 10, 8, 0), 4, Priorite.HAUTE, hugo, 60);
        terminee(a, LocalDateTime.of(2030, 3, 5, 8, 0), 2, Priorite.HAUTE, hugo, 60);
        terminee(b, LocalDateTime.of(2030, 3, 12, 8, 0), 10, Priorite.NORMALE, hugo, 60);
        terminee(b, LocalDateTime.of(2030, 3, 20, 8, 0), 20, Priorite.NORMALE, lea, 30);
        terminee(a, LocalDateTime.of(2030, 5, 2, 8, 0), 30, Priorite.NORMALE, lea, 30);
        terminee(c, LocalDateTime.of(2030, 5, 15, 8, 0), 60, Priorite.NORMALE, lea, 30);
        terminee(b, LocalDateTime.of(2030, 6, 3, 8, 0), 40, Priorite.NORMALE, lea, 30);
        annulee(c, LocalDateTime.of(2030, 5, 20, 8, 0));

        // Hors période : une panne de 2028, et une intervention de 2028 encore planifiée chez Hugo
        terminee(d, LocalDateTime.of(2028, 3, 1, 8, 0), 5, Priorite.BASSE, lea, 45);
        planifiee(d, LocalDateTime.of(2028, 3, 2, 8, 0), hugo);
    }

    // ------------------------------------------------------------------ jeu de données

    private Equipement equipement(String reference, String nom, TypeEquipement type, Zone zone) {
        return equipementRepository.saveAndFlush(new Equipement(reference, nom, LocalDate.of(2020, 1, 6), type, zone));
    }

    private Utilisateur utilisateur(String nom, String prenom, String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur(nom, prenom, email, "hash", role));
    }

    private void terminee(Equipement equipement, LocalDateTime demande, int heures, Priorite priorite,
                          Utilisateur technicien, int minutes) {
        inserer(equipement, "TERMINEE", priorite, demande, demande, demande.plusHours(heures), technicien, minutes, null);
    }

    private void annulee(Equipement equipement, LocalDateTime demande) {
        inserer(equipement, "ANNULEE", Priorite.NORMALE, demande, null, null, null, null, "Doublon");
    }

    private void planifiee(Equipement equipement, LocalDateTime demande, Utilisateur technicien) {
        inserer(equipement, "PLANIFIEE", Priorite.NORMALE, demande, null, null, technicien, null, null);
    }

    private void inserer(Equipement equipement, String statut, Priorite priorite, LocalDateTime demande,
                         LocalDateTime debut, LocalDateTime fin, Utilisateur technicien, Integer minutes, String motif) {
        jdbc.update("""
                INSERT INTO intervention (numero, titre, description, priorite, statut, date_demande,
                                          date_planifiee, date_debut, date_fin, temps_passe_min,
                                          compte_rendu, motif_annulation,
                                          id_equipement, id_demandeur, id_technicien, id_modifie_par)
                VALUES (?, 'Panne de test', 'Panne du jeu de reporting', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "RPT-" + (++numero), priorite.name(), statut, horodatage(demande),
                horodatage(technicien == null ? null : demande), horodatage(debut), horodatage(fin),
                new SqlParameterValue(Types.INTEGER, minutes),
                texte("TERMINEE".equals(statut) ? "Réparation effectuée" : null), texte(motif),
                equipement.getId(), demandeur.getId(),
                new SqlParameterValue(Types.NUMERIC, technicien == null ? null : technicien.getId()),
                demandeur.getId());
    }

    // Valeurs typées : le pilote Oracle doit connaître le type SQL d'un paramètre null
    private static SqlParameterValue horodatage(LocalDateTime date) {
        return new SqlParameterValue(Types.TIMESTAMP, date == null ? null : Timestamp.valueOf(date));
    }

    private static SqlParameterValue texte(String valeur) {
        return new SqlParameterValue(Types.VARCHAR, valeur);
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("Top équipements : RANK() donne le même rang aux ex aequo, puis saute un rang ; annulées exclues")
    void topEquipements() {
        assertThat(reporting.topEquipements(DEBUT_2030, JUILLET_2030, 10))
                .extracting(TopEquipement::rang, TopEquipement::reference, TopEquipement::nombrePannes)
                .containsExactly(
                        tuple(1, "RPT-A", 3L),
                        tuple(1, "RPT-B", 3L),
                        tuple(3, "RPT-C", 1L));
    }

    @Test
    @DisplayName("Top équipements : la limite porte sur le rang, les ex aequo restent ensemble")
    void topEquipementsLimite() {
        assertThat(reporting.topEquipements(DEBUT_2030, JUILLET_2030, 1))
                .extracting(TopEquipement::reference)
                .containsExactly("RPT-A", "RPT-B");
    }

    @Test
    @DisplayName("Évolution mensuelle : un mois sans demande compte 0, LAG calcule l'écart avec le mois précédent")
    void evolutionMensuelle() {
        assertThat(reporting.evolutionMensuelle(DEBUT_2030, JUILLET_2030))
                .extracting(EvolutionMensuelle::mois, EvolutionMensuelle::nombre, EvolutionMensuelle::variation)
                .containsExactly(
                        tuple(YearMonth.of(2030, 1), 0L, null),
                        tuple(YearMonth.of(2030, 2), 1L, 1L),
                        tuple(YearMonth.of(2030, 3), 3L, 2L),
                        tuple(YearMonth.of(2030, 4), 0L, -3L),
                        tuple(YearMonth.of(2030, 5), 3L, 3L),
                        tuple(YearMonth.of(2030, 6), 1L, -2L));
    }

    @Test
    @DisplayName("Temps moyen de résolution par priorité, de la plus urgente à la moins urgente")
    void tempsMoyenResolution() {
        assertThat(reporting.tempsMoyenResolution(DEBUT_2030, JUILLET_2030))
                .extracting(TempsResolution::priorite, TempsResolution::nombre, TempsResolution::heuresMoyennes)
                .containsExactly(
                        tuple(Priorite.HAUTE, 2L, 3.0),
                        tuple(Priorite.NORMALE, 5L, 32.0));
    }

    @Test
    @DisplayName("Charge par technicien : ouvertes à cet instant, terminées et minutes sur la période")
    void chargeTechniciens() {
        var charges = reporting.chargeTechniciens(DEBUT_2030, JUILLET_2030);

        assertThat(charges).filteredOn(c -> c.idTechnicien() == hugo.getId())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.nomComplet()).isEqualTo("Hugo Petit");
                    assertThat(c.ouvertes()).isEqualTo(1);
                    assertThat(c.terminees()).isEqualTo(3);
                    assertThat(c.minutesPassees()).isEqualTo(180);
                });
        assertThat(charges).filteredOn(c -> c.idTechnicien() == lea.getId())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.ouvertes()).isZero();
                    assertThat(c.terminees()).isEqualTo(4);   // la panne de 2028 est hors période
                    assertThat(c.minutesPassees()).isEqualTo(120);
                });
    }

    @Test
    @DisplayName("Charge par technicien : un technicien sans intervention apparaît avec des zéros")
    void technicienSansIntervention() {
        var nouveau = utilisateur("Faure", "Paul", "paul.rpt@mecadetect.fr", Role.TECHNICIEN);

        assertThat(reporting.chargeTechniciens(DEBUT_2030, JUILLET_2030))
                .filteredOn(c -> c.idTechnicien() == nouveau.getId())
                .singleElement()
                .satisfies(c -> assertThat(c.terminees() + c.ouvertes() + c.minutesPassees()).isZero());
    }
}