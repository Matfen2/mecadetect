package fr.mecadetect.reporting;

import java.time.YearMonth;

/// Nombre de demandes d'un mois et écart avec le mois précédent.
///
/// @param mois      le mois
/// @param nombre    nombre d'interventions demandées pendant le mois
/// @param variation écart avec le mois précédent ; null pour le premier mois de la période,
///                  qui n'a pas de mois précédent
public record EvolutionMensuelle(YearMonth mois, long nombre, Long variation) {
}