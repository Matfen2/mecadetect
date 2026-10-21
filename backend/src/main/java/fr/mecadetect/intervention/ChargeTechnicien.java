package fr.mecadetect.intervention;

/// Nombre d'interventions ouvertes d'un technicien, calculé par une requête groupée.
///
/// @param idTechnicien identifiant du technicien
/// @param nombre       nombre d'interventions ouvertes qui lui sont affectées
public record ChargeTechnicien(Long idTechnicien, Long nombre) {
}