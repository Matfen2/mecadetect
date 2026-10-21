package fr.mecadetect.intervention;

import java.time.LocalDateTime;

/// Ligne d'une liste d'interventions, projetée directement par la requête JPQL (UC-DB5) :
/// une seule requête, uniquement les colonnes affichées, aucune entité chargée.
///
/// @param idIntervention      identifiant de l'intervention
/// @param numero              numéro métier, de la forme INT-2026-000001
/// @param titre               titre de la panne
/// @param priorite            priorité
/// @param statut              statut actuel
/// @param dateDemande         date de la déclaration
/// @param datePlanifiee       date planifiée, null tant que l'intervention n'est pas planifiée
/// @param equipementReference référence de l'équipement
/// @param equipementNom       nom de l'équipement
/// @param idTechnicien        identifiant du technicien affecté, null s'il n'y en a pas
/// @param technicienPrenom    prénom du technicien affecté
/// @param technicienNom       nom du technicien affecté
public record InterventionResume(
        Long idIntervention,
        String numero,
        String titre,
        Priorite priorite,
        StatutIntervention statut,
        LocalDateTime dateDemande,
        LocalDateTime datePlanifiee,
        String equipementReference,
        String equipementNom,
        Long idTechnicien,
        String technicienPrenom,
        String technicienNom) {

    /// Nom complet du technicien affecté, pour l'affichage.
    ///
    /// @return le prénom suivi du nom, ou null si aucun technicien n'est affecté
    public String technicienNomComplet() {
        return technicienNom == null ? null : technicienPrenom + " " + technicienNom;
    }
}