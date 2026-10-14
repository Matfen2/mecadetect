package fr.mecadetect.dashboard;

import fr.mecadetect.intervention.Priorite;
import fr.mecadetect.reporting.ChargeTechnicienPeriode;
import fr.mecadetect.reporting.EvolutionMensuelle;
import fr.mecadetect.reporting.TopEquipement;

import java.util.List;
import java.util.SequencedMap;

/**
 * Les indicateurs du tableau de bord (tâche 5.1).
 * ouvertesParPriorite contient toujours les 4 priorités, de la plus urgente à la moins urgente.
 */
public record TableauDeBord(SequencedMap<Priorite, Long> ouvertesParPriorite,
                            long equipementsEnPanne,
                            double tempsMoyenResolutionHeures,
                            List<TopEquipement> topEquipements,
                            List<EvolutionMensuelle> evolutionMensuelle,
                            List<ChargeTechnicienPeriode> chargeTechniciens) {
}