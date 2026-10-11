package fr.mecadetect.intervention;

/** Données d'une déclaration de panne, indépendantes des DTO de l'API. */
public record DeclarationCommande(Long idEquipement, String titre, String description, Priorite priorite) {
}