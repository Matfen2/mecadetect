package fr.mecadetect.intervention;

import java.time.LocalDateTime;

/// Planification demandée par l'admin.
///
/// @param version       version de l'intervention connue de l'admin (verrou optimiste, UC-DB6)
/// @param idTechnicien  identifiant du technicien choisi
/// @param datePlanifiee date et heure prévues de l'intervention
public record PlanificationCommande(long version, Long idTechnicien, LocalDateTime datePlanifiee) {
}