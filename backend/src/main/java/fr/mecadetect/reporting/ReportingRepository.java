package fr.mecadetect.reporting;

import fr.mecadetect.intervention.Priorite;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Requêtes de reporting (UC-DB2, tâche 4.3), écrites en SQL Oracle natif :
 * les fonctions analytiques (RANK, LAG) et le calendrier par CONNECT BY n'ont pas d'équivalent JPQL.
 * Chaque ligne est projetée directement dans un record, sans passer par les entités.
 *
 * Les périodes sont des intervalles [depuis ; avant[ en UTC, comme toutes les dates stockées.
 */
@Repository
public class ReportingRepository {

    /** Alias SQL des comptages, lu dans plusieurs requêtes. */
    private static final String NOMBRE = "nombre";

    private final NamedParameterJdbcTemplate jdbc;

    public ReportingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Équipements les plus souvent en panne sur la période, interventions annulées exclues.
     * RANK() et non ROW_NUMBER() : deux équipements ex aequo ont le même rang, le suivant saute un rang
     * (1, 1, 3). Le classement peut donc compter plus de lignes que la limite en cas d'égalité.
     */
    public List<TopEquipement> topEquipements(LocalDateTime depuis, LocalDateTime avant, int limite) {
        return jdbc.query("""
                SELECT rang, reference, nom, nombre_pannes
                  FROM (SELECT e.reference, e.nom, COUNT(*) AS nombre_pannes,
                               RANK() OVER (ORDER BY COUNT(*) DESC) AS rang
                          FROM intervention i
                          JOIN equipement e ON e.id_equipement = i.id_equipement
                         WHERE i.date_demande >= :depuis AND i.date_demande < :avant
                           AND i.statut <> 'ANNULEE'
                         GROUP BY e.id_equipement, e.reference, e.nom)
                 WHERE rang <= :limite
                 ORDER BY rang, reference
                """,
                periode(depuis, avant).addValue("limite", limite),
                (rs, _) -> new TopEquipement(rs.getInt("rang"), rs.getString("reference"),
                        rs.getString("nom"), rs.getLong("nombre_pannes")));
    }

    /**
     * Demandes par mois et écart avec le mois précédent (LAG).
     * Le calendrier est généré par CONNECT BY : un mois sans demande apparaît avec 0,
     * sinon LAG comparerait deux mois non consécutifs.
     */
    public List<EvolutionMensuelle> evolutionMensuelle(LocalDateTime depuis, LocalDateTime avant) {
        return jdbc.query("""
                WITH mois AS (
                    SELECT ADD_MONTHS(TRUNC(:depuis, 'MM'), LEVEL - 1) AS debut
                      FROM dual
                   CONNECT BY ADD_MONTHS(TRUNC(:depuis, 'MM'), LEVEL - 1) < :avant
                ), comptes AS (
                    SELECT TRUNC(date_demande, 'MM') AS debut, COUNT(*) AS nombre
                      FROM intervention
                     WHERE date_demande >= :depuis AND date_demande < :avant
                     GROUP BY TRUNC(date_demande, 'MM')
                )
                SELECT m.debut,
                       NVL(c.nombre, 0) AS nombre,
                       NVL(c.nombre, 0) - LAG(NVL(c.nombre, 0)) OVER (ORDER BY m.debut) AS variation
                  FROM mois m
                  LEFT JOIN comptes c ON c.debut = m.debut
                 ORDER BY m.debut
                """,
                periode(depuis, avant),
                (rs, _) -> new EvolutionMensuelle(
                        YearMonth.from(rs.getTimestamp("debut").toLocalDateTime()),
                        rs.getLong(NOMBRE),
                        longOuNull(rs, "variation")));
    }

    /** Temps moyen de résolution (de la demande à la fin) des interventions terminées sur la période. */
    public List<TempsResolution> tempsMoyenResolution(LocalDateTime depuis, LocalDateTime avant) {
        return jdbc.query("""
                SELECT priorite, COUNT(*) AS nombre,
                       ROUND(AVG((CAST(date_fin AS DATE) - CAST(date_demande AS DATE)) * 24), 1) AS heures
                  FROM intervention
                 WHERE statut = 'TERMINEE'
                   AND date_fin >= :depuis AND date_fin < :avant
                 GROUP BY priorite
                 ORDER BY DECODE(priorite, 'CRITIQUE', 1, 'HAUTE', 2, 'NORMALE', 3, 4)
                """,
                periode(depuis, avant),
                (rs, _) -> new TempsResolution(Priorite.valueOf(rs.getString("priorite")),
                        rs.getLong(NOMBRE), rs.getDouble("heures")));
    }

    /**
     * Charge par technicien actif : interventions ouvertes (planifiées ou en cours) à cet instant,
     * interventions terminées et minutes passées sur la période. Un technicien sans intervention
     * apparaît avec des zéros (jointure externe).
     */
    public List<ChargeTechnicienPeriode> chargeTechniciens(LocalDateTime depuis, LocalDateTime avant) {
        return jdbc.query("""
                SELECT u.id_utilisateur, u.prenom, u.nom,
                       COUNT(CASE WHEN i.statut IN ('PLANIFIEE', 'EN_COURS') THEN 1 END) AS ouvertes,
                       COUNT(CASE WHEN i.statut = 'TERMINEE'
                                   AND i.date_fin >= :depuis AND i.date_fin < :avant THEN 1 END) AS terminees,
                       NVL(SUM(CASE WHEN i.statut = 'TERMINEE'
                                     AND i.date_fin >= :depuis AND i.date_fin < :avant
                                    THEN i.temps_passe_min END), 0) AS minutes
                  FROM utilisateur u
                  LEFT JOIN intervention i ON i.id_technicien = u.id_utilisateur
                 WHERE u.role = 'TECHNICIEN' AND u.actif = TRUE
                 GROUP BY u.id_utilisateur, u.prenom, u.nom
                 ORDER BY ouvertes DESC, terminees DESC, u.nom
                """,
                periode(depuis, avant),
                (rs, _) -> new ChargeTechnicienPeriode(rs.getLong("id_utilisateur"),
                        rs.getString("prenom"), rs.getString("nom"),
                        rs.getLong("ouvertes"), rs.getLong("terminees"), rs.getLong("minutes")));
    }

    /**
     * Interventions ouvertes (demandées, planifiées ou en cours) par priorité, à cet instant.
     * Une priorité sans intervention ouverte est absente du résultat.
     */
    public Map<Priorite, Long> ouvertesParPriorite() {
        Map<Priorite, Long> resultat = new EnumMap<>(Priorite.class);
        jdbc.query("""
                SELECT priorite, COUNT(*) AS nombre
                  FROM intervention
                 WHERE statut IN ('DEMANDEE', 'PLANIFIEE', 'EN_COURS')
                 GROUP BY priorite
                """,
                (RowCallbackHandler) rs -> resultat.put(Priorite.valueOf(rs.getString("priorite")), rs.getLong(NOMBRE)));
        return resultat;
    }

    /** Équipements actuellement en panne (statut tenu à jour par RG-05). */
    public long equipementsEnPanne() {
        return jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM equipement WHERE statut = 'EN_PANNE'", Long.class);
    }

    private static MapSqlParameterSource periode(LocalDateTime depuis, LocalDateTime avant) {
        return new MapSqlParameterSource()
                .addValue("depuis", Timestamp.valueOf(depuis))
                .addValue("avant", Timestamp.valueOf(avant));
    }

    private static Long longOuNull(ResultSet rs, String colonne) throws SQLException {
        long valeur = rs.getLong(colonne);
        return rs.wasNull() ? null : valeur;
    }
}