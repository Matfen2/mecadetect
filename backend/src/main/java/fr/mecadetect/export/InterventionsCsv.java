package fr.mecadetect.export;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Contenu du fichier CSV des interventions : la requête, l'en-tête et la conversion de chaque ligne.
final class InterventionsCsv {

    /// Le tri suit l'index (date_demande, id_intervention) : Oracle lit dans l'ordre, sans trier.
    private static final String REQUETE = """
            SELECT i.numero, i.titre, e.reference, e.nom AS equipement, i.priorite, i.statut,
                   d.prenom || ' ' || d.nom AS demandeur,
                   CASE WHEN t.id_utilisateur IS NOT NULL THEN t.prenom || ' ' || t.nom END AS technicien,
                   i.date_demande, i.date_planifiee, i.date_debut, i.date_fin, i.temps_passe_min,
                   i.compte_rendu, i.motif_annulation
              FROM intervention i
              JOIN equipement e ON e.id_equipement = i.id_equipement
              JOIN utilisateur d ON d.id_utilisateur = i.id_demandeur
              LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
             WHERE i.date_demande >= ? AND i.date_demande < ?
             ORDER BY i.date_demande, i.id_intervention
            """;

    static final List<String> ENTETE = List.of("Numéro", "Titre", "Référence équipement", "Équipement",
            "Priorité", "Statut", "Demandeur", "Technicien", "Demandée le", "Planifiée le", "Débutée le",
            "Terminée le", "Temps passé (min)", "Compte rendu", "Motif d'annulation");

    private static final Map<String, String> PRIORITES =
            Map.of("BASSE", "Basse", "NORMALE", "Normale", "HAUTE", "Haute", "CRITIQUE", "Critique");
    private static final Map<String, String> STATUTS = Map.of("DEMANDEE", "Demandée", "PLANIFIEE", "Planifiée",
            "EN_COURS", "En cours", "TERMINEE", "Terminée", "ANNULEE", "Annulée");
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private InterventionsCsv() {
    }

    /// Lignes du fichier, en-tête et BOM compris, produites à la demande.
    ///
    /// La requête est exécutée tout de suite, mais les lignes ne sont lues dans Oracle (par paquets,
    /// selon le fetch size du JdbcTemplate) qu'au fil de la consommation du [Stream]. Le Stream doit
    /// être fermé : c'est ce qui rend la connexion au pool.
    static Stream<String> lignes(JdbcTemplate jdbc, LocalDateTime depuisUtc, LocalDateTime avantUtc, ZoneId fuseau) {
        Stream<String> interventions = jdbc.queryForStream(REQUETE,
                (rs, numero) -> Csv.ligne(valeurs(rs, fuseau)),
                Timestamp.valueOf(depuisUtc), Timestamp.valueOf(avantUtc));
        return Stream.concat(Stream.of(Csv.BOM + Csv.ligne(ENTETE)), interventions);
    }

    private static List<String> valeurs(ResultSet rs, ZoneId fuseau) throws SQLException {
        Object temps = rs.getObject("temps_passe_min");
        return Arrays.asList(
                rs.getString("numero"),
                rs.getString("titre"),
                rs.getString("reference"),
                rs.getString("equipement"),
                PRIORITES.get(rs.getString("priorite")),
                STATUTS.get(rs.getString("statut")),
                rs.getString("demandeur"),
                rs.getString("technicien"),
                date(rs.getTimestamp("date_demande"), fuseau),
                date(rs.getTimestamp("date_planifiee"), fuseau),
                date(rs.getTimestamp("date_debut"), fuseau),
                date(rs.getTimestamp("date_fin"), fuseau),
                temps == null ? null : String.valueOf(((Number) temps).longValue()),
                rs.getString("compte_rendu"),
                rs.getString("motif_annulation"));
    }

    /// Dates stockées en UTC, écrites à l'heure locale de l'atelier.
    private static String date(Timestamp utc, ZoneId fuseau) {
        return utc == null ? null
                : utc.toLocalDateTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(fuseau).format(FORMAT_DATE);
    }
}
