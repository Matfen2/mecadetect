package fr.mecadetect.dashboard;

import fr.mecadetect.intervention.Priorite;
import fr.mecadetect.reporting.ChargeTechnicienPeriode;
import fr.mecadetect.reporting.EvolutionMensuelle;
import fr.mecadetect.reporting.TopEquipement;

import java.util.List;
import java.util.SequencedMap;

/// Les indicateurs du tableau de bord (tâche 5.1).
///
/// @param ouvertesParPriorite        interventions ouvertes par priorité : toujours les 4 priorités,
///                                   de la plus urgente à la moins urgente
/// @param equipementsEnPanne         nombre d'équipements actuellement en panne
/// @param tempsMoyenResolutionHeures temps moyen de résolution sur 12 mois, en heures
/// @param topEquipements             équipements les plus souvent en panne sur 12 mois
/// @param evolutionMensuelle         nombre de demandes de chacun des 12 derniers mois
/// @param chargeTechniciens          charge de chaque technicien sur le mois en cours
public record TableauDeBord(SequencedMap<Priorite, Long> ouvertesParPriorite,
                            long equipementsEnPanne,
                            double tempsMoyenResolutionHeures,
                            List<TopEquipement> topEquipements,
                            List<EvolutionMensuelle> evolutionMensuelle,
                            List<ChargeTechnicienPeriode> chargeTechniciens) {
}