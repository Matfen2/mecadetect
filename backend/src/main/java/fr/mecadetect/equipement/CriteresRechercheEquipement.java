package fr.mecadetect.equipement;

/**
 * Filtres de recherche, tous facultatifs.
 * Sans statut précisé, les équipements archivés sont exclus.
 */
public record CriteresRechercheEquipement(
        StatutEquipement statut,
        Long idType,
        Long idZone,
        String texte) {
}