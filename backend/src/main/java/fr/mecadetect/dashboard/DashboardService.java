package fr.mecadetect.dashboard;

import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.reporting.ReportingRepository;
import fr.mecadetect.reporting.TempsResolution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

/// Tableau de bord (tâche 5.1) : assemble les requêtes de reporting du Sprint 4.
///
/// Périodes, en UTC comme toutes les dates stockées :
///
/// - **12 mois glissants**, du 1er du mois il y a 11 mois jusqu'à la fin du mois en cours :
///   top des équipements, évolution mensuelle, temps moyen de résolution ;
/// - **le mois en cours** pour la charge des techniciens.
///
/// Les interventions archivées ont au moins un an : elles sont hors de ces périodes.
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ReportingRepository reporting;
    private final Clock clock;
    private final TableauDeBordProperties proprietes;

    /// Calcule l'ensemble des indicateurs du tableau de bord à la date courante.
    ///
    /// @return les indicateurs, sur les périodes décrites dans la documentation de la classe
    @Transactional(readOnly = true)
    public TableauDeBord calculer() {
        LocalDateTime debutMoisSuivant = LocalDate.now(clock).withDayOfMonth(1).plusMonths(1).atStartOfDay();
        LocalDateTime debutMois = debutMoisSuivant.minusMonths(1);
        LocalDateTime debutDouzeMois = debutMoisSuivant.minusMonths(12);

        return new TableauDeBord(
                ouvertesParPriorite(),
                reporting.equipementsEnPanne(),
                tempsMoyenGlobal(reporting.tempsMoyenResolution(debutDouzeMois, debutMoisSuivant)),
                reporting.topEquipements(debutDouzeMois, debutMoisSuivant, proprietes.tailleTop()),
                reporting.evolutionMensuelle(debutDouzeMois, debutMoisSuivant),
                reporting.chargeTechniciens(debutMois, debutMoisSuivant));
    }

    /// Nombre d'interventions ouvertes pour chaque priorité, dans l'ordre d'urgence de [Priorite].
    ///
    /// Une priorité sans intervention ouverte est présente avec la valeur 0.
    ///
    /// @return les compteurs, de la priorité la plus urgente à la moins urgente
    private SequencedMap<Priorite, Long> ouvertesParPriorite() {
        Map<Priorite, Long> comptes = reporting.ouvertesParPriorite();
        SequencedMap<Priorite, Long> ouvertes = new LinkedHashMap<>();
        for (Priorite priorite : Priorite.values()) {
            ouvertes.put(priorite, comptes.getOrDefault(priorite, 0L));
        }
        return ouvertes;
    }

    /// Moyenne des temps de résolution, pondérée par le nombre d'interventions de chaque priorité.
    ///
    /// @param temps temps moyen et nombre d'interventions par priorité
    /// @return la moyenne en heures, arrondie au dixième, ou 0 s'il n'y a aucune intervention
    static double tempsMoyenGlobal(List<TempsResolution> temps) {
        long total = temps.stream().mapToLong(TempsResolution::nombre).sum();
        if (total == 0) {
            return 0;
        }
        double somme = temps.stream().mapToDouble(t -> t.nombre() * t.heuresMoyennes()).sum();
        return Math.round(somme / total * 10) / 10.0;
    }
}