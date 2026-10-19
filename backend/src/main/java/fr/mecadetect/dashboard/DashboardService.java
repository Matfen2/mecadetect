package fr.mecadetect.dashboard;

import fr.mecadetect.intervention.Priorite;
import fr.mecadetect.reporting.ReportingRepository;
import fr.mecadetect.reporting.TempsResolution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

/**
 * Tableau de bord (tâche 5.1) : assemble les requêtes de reporting du Sprint 4.
 *
 * Périodes, en UTC comme toutes les dates stockées :
 * - 12 mois glissants, du 1er du mois il y a 11 mois jusqu'à la fin du mois en cours
 *   (top des équipements, évolution mensuelle, temps moyen de résolution) ;
 * - le mois en cours pour la charge des techniciens.
 * Les interventions archivées ont au moins un an : elles sont hors de ces périodes.
 */
@Service
public class DashboardService {

    static final int TAILLE_TOP = 10;
    private static final List<Priorite> ORDRE_URGENCE =
            List.of(Priorite.CRITIQUE, Priorite.HAUTE, Priorite.NORMALE, Priorite.BASSE);

    private final ReportingRepository reporting;
    private final Clock clock;

    public DashboardService(ReportingRepository reporting, Clock clock) {
        this.reporting = reporting;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TableauDeBord calculer() {
        LocalDateTime debutMoisSuivant = LocalDate.now(clock).withDayOfMonth(1).plusMonths(1).atStartOfDay();
        LocalDateTime debutMois = debutMoisSuivant.minusMonths(1);
        LocalDateTime debutDouzeMois = debutMoisSuivant.minusMonths(12);

        return new TableauDeBord(
                ouvertesParPriorite(),
                reporting.equipementsEnPanne(),
                tempsMoyenGlobal(reporting.tempsMoyenResolution(debutDouzeMois, debutMoisSuivant)),
                reporting.topEquipements(debutDouzeMois, debutMoisSuivant, TAILLE_TOP),
                reporting.evolutionMensuelle(debutDouzeMois, debutMoisSuivant),
                reporting.chargeTechniciens(debutMois, debutMoisSuivant));
    }

    /** Les 4 priorités dans l'ordre d'urgence, à 0 quand aucune intervention n'est ouverte. */
    private SequencedMap<Priorite, Long> ouvertesParPriorite() {
        Map<Priorite, Long> comptes = reporting.ouvertesParPriorite();
        SequencedMap<Priorite, Long> resultat = new LinkedHashMap<>();
        ORDRE_URGENCE.forEach(priorite -> resultat.put(priorite, comptes.getOrDefault(priorite, 0L)));
        return resultat;
    }

    /** Moyenne des temps par priorité, pondérée par leur nombre d'interventions, arrondie au dixième. */
    static double tempsMoyenGlobal(List<TempsResolution> temps) {
        long total = temps.stream().mapToLong(TempsResolution::nombre).sum();
        if (total == 0) {
            return 0;
        }
        double somme = temps.stream().mapToDouble(t -> t.nombre() * t.heuresMoyennes()).sum();
        return Math.round(somme / total * 10) / 10.0;
    }
}