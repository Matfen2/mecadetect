package fr.mecadetect.reporting;

import fr.mecadetect.intervention.Priorite;

/** Temps moyen entre la demande et la fin de l'intervention, pour une priorité. */
public record TempsResolution(Priorite priorite, long nombre, double heuresMoyennes) {
}