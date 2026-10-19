package fr.mecadetect.export;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
import java.util.Objects;

/**
 * Le fichier CSV, produit ligne par ligne pendant que le client le télécharge.
 *
 * Rien n'est chargé d'un bloc en mémoire : la requête est ouverte à la première lecture, chaque ligne
 * lue dans Oracle (par paquets de 500) est convertie en octets à la demande, et la connexion est
 * rendue au pool à la fin du fichier ou à la fermeture du flux (Spring ferme le flux après l'envoi,
 * y compris si le client interrompt le téléchargement).
 */
final class CsvInterventionsInputStream extends InputStream {

    /** Le tri suit l'index (date_demande, id_intervention) : Oracle lit dans l'ordre, sans trier. */
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

    /** Marque d'ordre des octets : sans elle, Excel lit le fichier en Windows-1252 et casse les accents. */
    private static final String BOM = "\uFEFF";

    private static final Map<String, String> PRIORITES =
            Map.of("BASSE", "Basse", "NORMALE", "Normale", "HAUTE", "Haute", "CRITIQUE", "Critique");
    private static final Map<String, String> STATUTS = Map.of("DEMANDEE", "Demandée", "PLANIFIEE", "Planifiée",
            "EN_COURS", "En cours", "TERMINEE", "Terminée", "ANNULEE", "Annulée");
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DataSource dataSource;
    private final LocalDateTime depuisUtc;
    private final LocalDateTime avantUtc;
    private final ZoneId fuseau;

    private Connection connexion;
    private PreparedStatement instruction;
    private ResultSet lignes;
    private byte[] tampon;
    private int position;
    private boolean termine;
    private boolean ferme;

    CsvInterventionsInputStream(DataSource dataSource, LocalDateTime depuisUtc, LocalDateTime avantUtc, ZoneId fuseau) {
        this.dataSource = dataSource;
        this.depuisUtc = depuisUtc;
        this.avantUtc = avantUtc;
        this.fuseau = fuseau;
        this.tampon = (BOM + Csv.ligne(ENTETE)).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public int read() throws IOException {
        return disponible() ? tampon[position++] & 0xFF : -1;
    }

    @Override
    public int read(byte[] destination, int debut, int longueur) throws IOException {
        Objects.checkFromIndexSize(debut, longueur, destination.length);
        if (longueur == 0) {
            return 0;
        }
        if (!disponible()) {
            return -1;
        }
        int copies = Math.min(longueur, tampon.length - position);
        System.arraycopy(tampon, position, destination, debut, copies);
        position += copies;
        return copies;
    }

    @Override
    public void close() {
        fermer();
    }

    /** Remplit le tampon avec la ligne suivante si besoin ; false à la fin du fichier. */
    private boolean disponible() throws IOException {
        while (position >= tampon.length) {
            if (termine) {
                return false;
            }
            ligneSuivante();
        }
        return true;
    }

    private void ligneSuivante() throws IOException {
        try {
            if (lignes == null) {
                ouvrir();
            }
            if (lignes.next()) {
                tampon = Csv.ligne(valeurs(lignes)).getBytes(StandardCharsets.UTF_8);
                position = 0;
            } else {
                termine = true;
                fermer();
            }
        } catch (SQLException e) {
            termine = true;
            fermer();
            throw new IOException("Lecture des interventions à exporter impossible", e);
        }
    }

    private void ouvrir() throws SQLException {
        // Connexion de la transaction en cours s'il y en a une (tests), sinon une connexion du pool
        connexion = DataSourceUtils.getConnection(dataSource);
        instruction = connexion.prepareStatement(REQUETE);
        instruction.setFetchSize(500);
        instruction.setTimestamp(1, Timestamp.valueOf(depuisUtc));
        instruction.setTimestamp(2, Timestamp.valueOf(avantUtc));
        lignes = instruction.executeQuery();
    }

    private void fermer() {
        if (ferme) {
            return;
        }
        ferme = true;
        JdbcUtils.closeResultSet(lignes);
        JdbcUtils.closeStatement(instruction);
        if (connexion != null) {
            DataSourceUtils.releaseConnection(connexion, dataSource);
        }
    }

    private List<String> valeurs(ResultSet rs) throws SQLException {
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
                date(rs.getTimestamp("date_demande")),
                date(rs.getTimestamp("date_planifiee")),
                date(rs.getTimestamp("date_debut")),
                date(rs.getTimestamp("date_fin")),
                temps == null ? null : String.valueOf(((Number) temps).longValue()),
                rs.getString("compte_rendu"),
                rs.getString("motif_annulation"));
    }

    /** Dates stockées en UTC, écrites à l'heure locale de l'atelier. */
    private String date(Timestamp utc) {
        return utc == null ? null
                : utc.toLocalDateTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(fuseau).format(FORMAT_DATE);
    }
}