package fr.mecadetect.equipement.importation;

import java.io.Serializable;

/// Ligne écartée pendant l'import, avec la raison du rejet.
///
/// Sérialisable : les rejets sont rangés dans le contexte d'exécution du step, qu'un dépôt
/// de jobs en base (JDBC) enregistrerait.
///
/// @param ligne numéro de la ligne dans le fichier, en-tête compris
/// @param motif raison du rejet
public record RejetImport(long ligne, String motif) implements Serializable {
}
