package fr.mecadetect.equipement.importation;

import java.util.List;

/// Rapport d'un import du parc.
///
/// @param lignesLues lignes de données lues, en-tête exclu
/// @param importes   équipements créés
/// @param rejetes    lignes rejetées
/// @param rejets     détail des rejets, triés par numéro de ligne (les 100 premiers au plus)
public record ResultatImport(long lignesLues, long importes, long rejetes, List<RejetImport> rejets) {
}
