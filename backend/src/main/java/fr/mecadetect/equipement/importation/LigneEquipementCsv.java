package fr.mecadetect.equipement.importation;

/// Ligne du fichier d'import, telle que lue : des textes bruts, pas encore validés.
///
/// @param numeroLigne     numéro de la ligne dans le fichier, en-tête compris (comme dans un tableur)
/// @param reference       référence de l'équipement
/// @param nom             nom de l'équipement
/// @param dateMiseService date de mise en service, au format AAAA-MM-JJ ou JJ/MM/AAAA
/// @param type            libellé du type d'équipement
/// @param zone            code de la zone d'implantation
public record LigneEquipementCsv(
        int numeroLigne,
        String reference,
        String nom,
        String dateMiseService,
        String type,
        String zone) {
}
