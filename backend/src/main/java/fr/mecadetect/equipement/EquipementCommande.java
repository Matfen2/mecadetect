package fr.mecadetect.equipement;

import java.time.LocalDate;

/**
 * Données nécessaires pour créer ou modifier un équipement.
 * Le service ne dépend pas des DTO générés : si le contrat d'API évolue,
 * seul le contrôleur est impacté.
 */
public record EquipementCommande(
        String reference,
        String nom,
        LocalDate dateMiseService,
        Long idType,
        Long idZone) {
}