package fr.mecadetect.equipement;

import java.time.LocalDate;

/// Données nécessaires pour créer ou modifier un équipement.
/// Le service ne dépend pas des DTO générés : si le contrat d'API évolue,
/// seul le contrôleur est impacté.
///
/// @param reference       référence unique de l'équipement
/// @param nom             nom affiché
/// @param dateMiseService date de mise en service
/// @param idType          identifiant du type d'équipement
/// @param idZone          identifiant de la zone d'implantation
public record EquipementCommande(
        String reference,
        String nom,
        LocalDate dateMiseService,
        Long idType,
        Long idZone) {
}