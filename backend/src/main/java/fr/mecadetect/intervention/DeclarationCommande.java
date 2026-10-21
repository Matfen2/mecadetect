package fr.mecadetect.intervention;

/// Données d'une déclaration de panne, indépendantes des DTO de l'API.
///
/// @param idEquipement identifiant de l'équipement en panne
/// @param titre        titre court de la panne
/// @param description  description détaillée
/// @param priorite     priorité demandée
public record DeclarationCommande(Long idEquipement, String titre, String description, Priorite priorite) {
}