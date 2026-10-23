package fr.mecadetect.equipement;

/// Filtres de recherche, tous facultatifs.
/// Sans statut précisé, les équipements archivés sont exclus.
///
/// @param statut statut recherché ; null exclut les équipements archivés
/// @param idType identifiant du type d'équipement
/// @param idZone identifiant de la zone
/// @param texte  texte cherché dans la référence ou le nom, sans tenir compte de la casse
public record CriteresRechercheEquipement(
        StatutEquipement statut,
        Long idType,
        Long idZone,
        String texte) {
}