package fr.mecadetect.export;

import org.springframework.core.io.Resource;

/// Un fichier CSV prêt à télécharger, produit pendant l'envoi.
///
/// @param nomFichier nom proposé au téléchargement
/// @param contenu    contenu du fichier, lu à la demande
public record ExportCsv(String nomFichier, Resource contenu) {
}