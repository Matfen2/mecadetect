package fr.mecadetect.intervention.domain;

/// Nombre d'interventions ouvertes d'un technicien, calculé par une requête groupée.
///
/// @param idTechnicien identifiant du technicien
/// @param nombre       nombre d'interventions ouvertes qui lui sont affectées
public record ChargeTechnicien(Long idTechnicien, Long nombre) {
}