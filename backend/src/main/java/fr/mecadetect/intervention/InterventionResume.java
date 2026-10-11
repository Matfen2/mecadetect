package fr.mecadetect.intervention;

import java.time.LocalDateTime;

/**
 * Ligne d'une liste d'interventions, projetée directement par la requête JPQL (UC-DB5) :
 * une seule requête, uniquement les colonnes affichées, aucune entité chargée.
 */
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
        String technicienPrenom,
        String technicienNom) {

    public String technicienNomComplet() {
        return technicienNom == null ? null : technicienPrenom + " " + technicienNom;
    }
}