package fr.mecadetect.reporting;

import fr.mecadetect.intervention.Priorite;

/// Temps moyen entre la demande et la fin de l'intervention, pour une priorité.
///
/// @param priorite       la priorité
/// @param nombre         nombre d'interventions terminées de cette priorité
/// @param heuresMoyennes temps moyen de résolution, en heures
public record TempsResolution(Priorite priorite, long nombre, double heuresMoyennes) {
}