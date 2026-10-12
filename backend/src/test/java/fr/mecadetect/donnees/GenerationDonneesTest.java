package fr.mecadetect.donnees;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.intervention.Intervention;
import fr.mecadetect.intervention.InterventionRepository;
import fr.mecadetect.intervention.Priorite;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import fr.mecadetect.utilisateur.Habilitation;
import fr.mecadetect.utilisateur.HabilitationRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tâche 4.1 : PKG_GENERATION crée un historique réaliste et cohérent.
 * Le test génère 3 000 interventions en 3 lots, sans commit (p_commit => FALSE) :
 * tout est annulé à la fin du test, comme pour les autres tests @Transactional.
 * Les vérifications portent sur les seules interventions générées (identifiant > maximum avant génération).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class GenerationDonneesTest {

    private static final int NOMBRE = 3000;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private InterventionRepository interventionRepository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;

    private Equipement presse;
    private Equipement archive;
    private Utilisateur demandeur;
    private long maximumAvant;

    @BeforeEach
    void setUp() {
        var zone = zoneRepository.saveAndFlush(new Zone("G1", "Zone de génération"));
        var presses = typeRepository.saveAndFlush(new TypeEquipement("Presse générée"));
        var tours = typeRepository.saveAndFlush(new TypeEquipement("Tour généré"));
        presse = equipementRepository.saveAndFlush(
                new Equipement("GEN-P01", "Presse de génération", LocalDate.of(2018, 1, 8), presses, zone));
        equipementRepository.saveAndFlush(
                new Equipement("GEN-T01", "Tour de génération", LocalDate.of(2019, 1, 7), tours, zone));
        archive = new Equipement("GEN-A01", "Équipement archivé", LocalDate.of(2010, 1, 4), presses, zone);
        archive.archiver();
        archive = equipementRepository.saveAndFlush(archive);

        utilisateur("admin.gen@mecadetect.fr", Role.ADMIN);
        demandeur = utilisateur("demandeur.gen@mecadetect.fr", Role.DEMANDEUR);
        var technicien = utilisateur("technicien.gen@mecadetect.fr", Role.TECHNICIEN);
        // Habilité sur les presses seulement : le générateur doit couvrir les tours lui-même
        habilitationRepository.saveAndFlush(new Habilitation(presses, technicien, LocalDate.of(2024, 9, 2)));

        maximumAvant = jdbc.queryForObject("SELECT NVL(MAX(id_intervention), 0) FROM intervention", Long.class);
    }

    private Utilisateur utilisateur(String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur("Test", "Génération", email, "hash", role));
    }

    private void generer() {
        jdbc.execute("""
                BEGIN
                    pkg_generation.generer_interventions(
                        p_nombre => %d, p_annees => 5, p_lot => 1000, p_techniciens => 3, p_commit => FALSE);
                END;
                """.formatted(NOMBRE));
    }

    /** Compte les interventions générées qui vérifient la condition SQL donnée (alias i). */
    private long compter(String condition) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM intervention i WHERE i.id_intervention > ? AND (" + condition + ")",
                Long.class, maximumAvant);
    }

    @Test
    @DisplayName("Crée le nombre demandé, en plusieurs lots, avec des numéros uniques INT-AAAA-NNNNNN de l'année de demande")
    void nombreEtNumeros() {
        generer();

        assertThat(compter("1 = 1")).isEqualTo(NOMBRE);
        assertThat(compter("NOT REGEXP_LIKE(i.numero, '^INT-[0-9]{4}-[0-9]{6,}$')")).isZero();
        assertThat(compter("SUBSTR(i.numero, 5, 4) <> TO_CHAR(i.date_demande, 'YYYY')")).isZero();
    }

    @Test
    @DisplayName("Les demandes couvrent la période et les dates suivent l'ordre du cycle de vie")
    void dates() {
        generer();

        assertThat(compter("i.date_demande < SYS_EXTRACT_UTC(SYSTIMESTAMP) - INTERVAL '1827' DAY(4)")).isZero();
        assertThat(compter("i.date_demande > SYS_EXTRACT_UTC(SYSTIMESTAMP)")).isZero();
        // L'historique s'étale bien sur plusieurs années
        assertThat(compter("i.date_demande < SYS_EXTRACT_UTC(SYSTIMESTAMP) - INTERVAL '1460' DAY(4)")).isPositive();
        assertThat(compter("""
                i.statut = 'TERMINEE' AND NOT (i.date_demande <= i.date_planifiee
                    AND i.date_planifiee <= i.date_debut AND i.date_debut <= i.date_fin
                    AND i.date_fin <= SYS_EXTRACT_UTC(SYSTIMESTAMP))
                """)).isZero();
    }

    @Test
    @DisplayName("Statuts réalistes : les demandes anciennes sont closes, aucune intervention en cours (RG-04)")
    void statuts() {
        generer();

        assertThat(compter("i.statut = 'EN_COURS'")).isZero();
        assertThat(compter("""
                i.statut IN ('DEMANDEE', 'PLANIFIEE')
                AND i.date_demande < SYS_EXTRACT_UTC(SYSTIMESTAMP) - INTERVAL '31' DAY
                """)).isZero();
        assertThat(compter("i.statut = 'TERMINEE'")).isGreaterThan(NOMBRE * 3L / 4);
        assertThat(compter("i.statut = 'ANNULEE'")).isPositive();
    }

    @Test
    @DisplayName("Chaque technicien assigné est habilité sur le type de l'équipement (RG-09), jamais d'équipement archivé")
    void habilitationsEtEquipements() {
        generer();

        assertThat(compter("""
                i.id_technicien IS NOT NULL AND NOT EXISTS (
                    SELECT 1 FROM habilitation h JOIN equipement e ON e.id_type = h.id_type
                     WHERE e.id_equipement = i.id_equipement AND h.id_technicien = i.id_technicien)
                """)).isZero();
        assertThat(compter("i.id_equipement = " + archive.getId())).isZero();
    }

    @Test
    @DisplayName("Des techniciens sont créés si besoin, et chaque type a au moins un technicien habilité")
    void techniciensCompletes() {
        generer();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM utilisateur WHERE role = 'TECHNICIEN' AND actif = TRUE", Long.class))
                .isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM type_equipement t
                 WHERE NOT EXISTS (SELECT 1 FROM habilitation h WHERE h.id_type = t.id_type)
                """, Long.class)).isZero();
    }

    @Test
    @DisplayName("L'historique suit le parcours : nombre de lignes par statut, dernier statut identique, rien du trigger")
    void historique() {
        generer();

        long attendu = jdbc.queryForObject("""
                SELECT SUM(CASE i.statut
                               WHEN 'DEMANDEE'  THEN 1
                               WHEN 'PLANIFIEE' THEN 2
                               WHEN 'TERMINEE'  THEN 4
                               ELSE CASE WHEN i.id_technicien IS NULL THEN 2 ELSE 3 END END)
                  FROM intervention i WHERE i.id_intervention > ?
                """, Long.class, maximumAvant);
        long ecrit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM historique_statut WHERE id_intervention > ?", Long.class, maximumAvant);
        assertThat(ecrit).isEqualTo(attendu);

        assertThat(compter("""
                i.statut <> (SELECT MAX(h.nouveau_statut) KEEP (DENSE_RANK LAST ORDER BY h.date_changement, h.id_historique)
                               FROM historique_statut h WHERE h.id_intervention = i.id_intervention)
                """)).isZero();
        assertThat(compter("""
                EXISTS (SELECT 1 FROM historique_statut h
                         WHERE h.id_intervention = i.id_intervention AND h.date_changement < i.date_demande)
                """)).isZero();
    }

    @Test
    @DisplayName("RG-05 : après génération, le statut de chaque équipement suit ses interventions ouvertes")
    void statutDesEquipements() {
        generer();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM equipement e
                 WHERE e.statut <> 'ARCHIVE'
                   AND e.statut <> CASE
                         WHEN EXISTS (SELECT 1 FROM intervention i
                                       WHERE i.id_equipement = e.id_equipement AND i.statut = 'EN_COURS') THEN 'EN_MAINTENANCE'
                         WHEN EXISTS (SELECT 1 FROM intervention i
                                       WHERE i.id_equipement = e.id_equipement AND i.statut IN ('DEMANDEE', 'PLANIFIEE')) THEN 'EN_PANNE'
                         ELSE 'EN_SERVICE' END
                """, Long.class)).isZero();
    }

    @Test
    @DisplayName("Après la génération, le trigger historise de nouveau les changements de la session")
    void triggerReactive() {
        generer();

        var intervention = interventionRepository.saveAndFlush(new Intervention(
                interventionRepository.prochainNumero(2026), "Fuite d'huile", "Fuite sur le vérin principal",
                Priorite.HAUTE, presse, demandeur, LocalDateTime.now(ZoneOffset.UTC)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historique_statut WHERE id_intervention = ?",
                Long.class, intervention.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Des paramètres invalides sont refusés avec un message explicite")
    void parametresInvalides() {
        assertThatThrownBy(() -> jdbc.execute("BEGIN pkg_generation.generer_interventions(p_nombre => 0); END;"))
                .hasMessageContaining("ORA-20003");
    }
}