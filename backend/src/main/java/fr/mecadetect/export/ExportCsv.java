package fr.mecadetect.export;

import org.springframework.core.io.Resource;

/** Un fichier CSV prêt à télécharger : son nom et son contenu, produit pendant l'envoi. */
public record ExportCsv(String nomFichier, Resource contenu) {
}