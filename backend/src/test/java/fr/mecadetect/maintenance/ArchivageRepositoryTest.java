package fr.mecadetect.maintenance;

import fr.mecadetect.TestPersistanceOracle;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tâche 4.5 : `PKG_MAINTENANCE.ARCHIVER_INTERVENTIONS`, appelé par `SimpleJdbcCall`.
///
/// Le test tourne dans une transaction Spring : le repository passe alors `p_commit => FALSE`
/// et tout est annulé à la fin du test. Seul le journal, écrit en transaction autonome,
/// est conservé : les assertions portent donc sur la dernière ligne du journal.
/// Les données de test datent de 2015, loin des données des autres tests.
@TestPersistanceOracle
class ArchivageRepositoryTest {

    private static final LocalDate LIMITE = LocalDate.of(2016, 1, 1);

    @Autowired private ArchivageRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private Equipement presse;
    private Utilisateur demandeur;
    private Utilisateur technicien;

    private long termineeAncienne;
    private long annuleeAncienne;
    private long termineeApresLimite;
    private long ouverteAncienne;
    private long planifieeAncienne;

    @BeforeEach
    void setUp() {
        var zone = zoneRepository.saveAndFlush(new Zone("AR", "Zone d'archivage"));
        var presses = typeRepository.saveAndFlush(new TypeEquipement("Presse à archiver"));
        presse = equipementRepository.saveAndFlush(
                new Equipement("ARC-P01", "Presse ancienne", LocalDate.of(2012, 1, 2), presses, zone));
        demandeur = utilisateurRepository.saveAndFlush(
                new Utilisateur("Moreau", "Sophie", "demandeur.arc@mecadetect.fr", "hash", Role.DEMANDEUR));
        technicien = utilisateurRepository.saveAndFlush(
                new Utilisateur("Bernard", "Lucas", "technicien.arc@mecadetect.fr", "hash", Role.TECHNICIEN));

        // Archivables : terminée avant la limite, annulée sur une demande antérieure à la limite
        termineeAncienne = intervention("INT-2015-900001", "TERMINEE",
                LocalDateTime.of(2015, 3, 2, 8, 0), LocalDateTime.of(2015, 3, 4, 17, 0));
        annuleeAncienne = intervention("INT-2015-900002", "ANNULEE",
                LocalDateTime.of(2015, 4, 6, 9, 0), null);
        // À conserver : terminée après la limite, et des interventions encore ouvertes, même très anciennes
        termineeApresLimite = intervention("INT-2015-900003", "TERMINEE",
                LocalDateTime.of(2015, 12, 21, 8, 0), LocalDateTime.of(2016, 1, 4, 11, 0));
        ouverteAncienne = intervention("INT-2015-900004", "DEMANDEE",
                LocalDateTime.of(2015, 1, 5, 10, 0), null);
        planifieeAncienne = intervention("INT-2015-900005", "PLANIFIEE",
                LocalDateTime.of(2015, 2, 2, 10, 0), null);
    }

    /**
     * Crée l'intervention en DEMANDEE puis la passe au statut voulu :
     * le trigger de V3 écrit deux lignes d'historique pour une intervention close.
     */
    private long intervention(String numero, String statut, LocalDateTime demande, LocalDateTime fin) {
        jdbc.update("""
                INSERT INTO intervention (numero, titre, description, priorite, statut, date_demande,
                                          id_equipement, id_demandeur, id_modifie_par)
                VALUES (?, 'Fuite d''huile', 'Fuite sur le vérin principal', 'NORMALE', 'DEMANDEE', ?, ?, ?, ?)
                """, numero, Timestamp.valueOf(demande), presse.getId(), demandeur.getId(), demandeur.getId());
        long id = jdbc.queryForObject("SELECT id_intervention FROM intervention WHERE numero = ?", Long.class, numero);

        switch (statut) {
            case "TERMINEE" -> jdbc.update("""
                    UPDATE intervention
                       SET statut = 'TERMINEE', id_technicien = ?, date_planifiee = ?, date_debut = ?, date_fin = ?,
                           temps_passe_min = 90, compte_rendu = 'Joint remplacé', id_modifie_par = ?
                     WHERE id_intervention = ?
                    """, technicien.getId(), Timestamp.valueOf(demande), Timestamp.valueOf(fin.minusHours(2)),
                    Timestamp.valueOf(fin), technicien.getId(), id);
            case "ANNULEE" -> jdbc.update("""
                    UPDATE intervention SET statut = 'ANNULEE', motif_annulation = 'Doublon', id_modifie_par = ?
                     WHERE id_intervention = ?
                    """, demandeur.getId(), id);
            case "PLANIFIEE" -> jdbc.update("""
                    UPDATE intervention SET statut = 'PLANIFIEE', id_technicien = ?, date_planifiee = ?, id_modifie_par = ?
                     WHERE id_intervention = ?
                    """, technicien.getId(), Timestamp.valueOf(demande.plusDays(1)), demandeur.getId(), id);
            default -> { }
        }
        return id;
    }

    private boolean dansIntervention(long id) {
        return compter("SELECT COUNT(*) FROM intervention WHERE id_intervention = ?", id) == 1;
    }

    private boolean dansArchive(long id) {
        return compter("SELECT COUNT(*) FROM intervention_archive WHERE id_intervention = ?", id) == 1;
    }

    private long compter(String sql, Object... parametres) {
        return jdbc.queryForObject(sql, Long.class, parametres);
    }

    private Map<String, Object> dernierJournal() {
        return jdbc.queryForMap("""
                SELECT statut, interventions, historiques, date_limite, date_fin, message
                  FROM journal_archivage ORDER BY id_journal DESC FETCH FIRST 1 ROW ONLY
                """);
    }

    @Test
    @DisplayName("Archive les interventions terminées avant la date et les annulées demandées avant la date")
    void archiveLesInterventionsCloses() {
        var resultat = repository.archiver(LIMITE, 10_000);

        assertThat(resultat.interventions()).isEqualTo(2);
        assertThat(dansIntervention(termineeAncienne)).isFalse();
        assertThat(dansIntervention(annuleeAncienne)).isFalse();
        assertThat(dansArchive(termineeAncienne)).isTrue();
        assertThat(dansArchive(annuleeAncienne)).isTrue();

        var archivee = jdbc.queryForMap("""
                SELECT numero, statut, compte_rendu, temps_passe_min, id_technicien, date_archivage
                  FROM intervention_archive WHERE id_intervention = ?
                """, termineeAncienne);
        assertThat(archivee.get("NUMERO")).isEqualTo("INT-2015-900001");
        assertThat(archivee.get("STATUT")).isEqualTo("TERMINEE");
        assertThat(archivee.get("COMPTE_RENDU")).isEqualTo("Joint remplacé");
        assertThat(((Number) archivee.get("ID_TECHNICIEN")).longValue()).isEqualTo(technicien.getId());
        assertThat(archivee.get("DATE_ARCHIVAGE")).isNotNull();
    }

    @Test
    @DisplayName("Ne touche ni aux interventions ouvertes, même anciennes, ni à celles closes après la date")
    void conserveLesAutres() {
        repository.archiver(LIMITE, 10_000);

        assertThat(dansIntervention(termineeApresLimite)).isTrue();
        assertThat(dansIntervention(ouverteAncienne)).isTrue();
        assertThat(dansIntervention(planifieeAncienne)).isTrue();
        assertThat(dansArchive(termineeApresLimite)).isFalse();
        assertThat(dansArchive(ouverteAncienne)).isFalse();
    }

    @Test
    @DisplayName("L'historique des statuts suit l'intervention dans l'archive, avec les mêmes identifiants")
    void deplaceLHistorique() {
        List<Long> historiqueAvant = jdbc.queryForList(
                "SELECT id_historique FROM historique_statut WHERE id_intervention = ? ORDER BY id_historique",
                Long.class, termineeAncienne);
        assertThat(historiqueAvant).hasSize(2);

        var resultat = repository.archiver(LIMITE, 10_000);

        assertThat(resultat.historiques()).isEqualTo(4);
        assertThat(compter("SELECT COUNT(*) FROM historique_statut WHERE id_intervention IN (?, ?)",
                termineeAncienne, annuleeAncienne)).isZero();
        assertThat(jdbc.queryForList(
                "SELECT id_historique FROM historique_statut_archive WHERE id_intervention = ? ORDER BY id_historique",
                Long.class, termineeAncienne)).isEqualTo(historiqueAvant);
        assertThat(compter("SELECT COUNT(*) FROM historique_statut WHERE id_intervention = ?", termineeApresLimite))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Traite par lots : avec des lots de 1, le résultat est le même et le journal note un succès")
    void parLotsAvecJournal() {
        var resultat = repository.archiver(LIMITE, 1);

        assertThat(resultat.interventions()).isEqualTo(2);
        assertThat(resultat.historiques()).isEqualTo(4);
        var journal = dernierJournal();
        assertThat(journal.get("STATUT")).isEqualTo("SUCCES");
        assertThat(((Number) journal.get("INTERVENTIONS")).longValue()).isEqualTo(2);
        assertThat(((Number) journal.get("HISTORIQUES")).longValue()).isEqualTo(4);
        assertThat(journal.get("DATE_FIN")).isNotNull();
    }

    @Test
    @DisplayName("Un second passage n'archive plus rien")
    void idempotent() {
        repository.archiver(LIMITE, 10_000);

        var second = repository.archiver(LIMITE, 10_000);

        assertThat(second.interventions()).isZero();
        assertThat(second.historiques()).isZero();
    }

    @Test
    @DisplayName("Une date limite future est refusée par le package (ORA-20010), journalisée en échec, sans rien archiver")
    void dateFutureRefusee() {
        assertThatThrownBy(() -> repository.archiver(LocalDate.now().plusDays(10), 10_000))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ORA-20010");

        assertThat(dernierJournal().get("STATUT")).isEqualTo("ECHEC");
        assertThat((String) dernierJournal().get("MESSAGE")).contains("ORA-20010");
        assertThat(dansIntervention(termineeAncienne)).isTrue();
    }
}