package fr.mecadetect.reporting;

import java.time.YearMonth;

/**
 * Nombre de demandes d'un mois et écart avec le mois précédent.
 * L'écart est null pour le premier mois de la période : il n'a pas de mois précédent.
 */
public record EvolutionMensuelle(YearMonth mois, long nombre, Long variation) {
}