package fr.mecadetect.export;

import fr.mecadetect.erreur.DonneesInvalidesException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/// Export CSV des interventions demandées sur une période (tâche 5.3).
///
/// La période et les dates du fichier sont à l'heure de l'atelier (Europe/Paris) : le fichier est lu
/// par une personne, dans Excel, et doit montrer les mêmes heures que l'application. En base, les
/// dates restent en UTC ; la conversion se fait aux bornes de la période et à l'écriture des lignes.
@Service
public class ExportInterventionsService {

    static final ZoneId FUSEAU = ZoneId.of("Europe/Paris");
    /// Un an au plus (environ 100 000 lignes sur le jeu de démonstration) : un export reste un fichier raisonnable.
    static final int JOURS_MAX = 366;

    /// Lignes lues par paquets de 500 : peu d'allers-retours avec Oracle, peu de mémoire.
    static final int TAILLE_PAQUET = 500;

    private final JdbcTemplate jdbc;

    public ExportInterventionsService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setFetchSize(TAILLE_PAQUET);
    }

    /// Prépare l'export des interventions demandées entre deux dates. Le fichier est produit
    /// pendant le téléchargement : rien n'est chargé en mémoire à l'avance.
    ///
    /// @param depuis premier jour de la période, inclus
    /// @param jusqua dernier jour de la période, inclus
    /// @return le nom du fichier et son contenu, lu à la demande
    /// @throws DonneesInvalidesException si la période est inversée ou dépasse 366 jours
    public ExportCsv exporter(LocalDate depuis, LocalDate jusqua) {
        if (jusqua.isBefore(depuis)) {
            throw new DonneesInvalidesException("PERIODE_INVALIDE",
                    "La date de fin doit être postérieure ou égale à la date de début.");
        }
        if (ChronoUnit.DAYS.between(depuis, jusqua) + 1 > JOURS_MAX) {
            throw new DonneesInvalidesException("PERIODE_TROP_LONGUE",
                    "La période exportée est limitée à " + JOURS_MAX + " jours.");
        }
        var lignes = InterventionsCsv.lignes(jdbc, debutEnUtc(depuis), debutEnUtc(jusqua.plusDays(1)), FUSEAU);
        var contenu = Csv.enFlux(lignes);
        // InputStreamResource exactement : Spring n'essaie pas d'en calculer la taille et l'envoie en flux
        return new ExportCsv("interventions-%s-au-%s.csv".formatted(depuis, jusqua), new InputStreamResource(contenu));
    }

    /// Minuit à Paris, exprimé en UTC comme les dates stockées.
    private static LocalDateTime debutEnUtc(LocalDate jour) {
        return jour.atStartOfDay(FUSEAU).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}