package fr.mecadetect.export;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Écriture CSV pour Excel en français : séparateur « ; » (la virgule est le séparateur décimal),
 * fin de ligne CRLF, guillemets autour des champs qui en ont besoin (RFC 4180).
 */
final class Csv {

    static final String SEPARATEUR = ";";
    static final String FIN_DE_LIGNE = "\r\n";

    /** Premiers caractères qu'Excel interprète comme le début d'une formule. */
    private static final String DEBUTS_DE_FORMULE = "=+-@\t\r";

    private Csv() {
    }

    /**
     * Un champ prêt à écrire. Contre l'injection de formule (CSV injection), un texte saisi par un
     * utilisateur qui commence par = + - @ est précédé d'une apostrophe : Excel l'affiche sans l'exécuter.
     */
    static String champ(String valeur) {
        if (valeur == null || valeur.isEmpty()) {
            return "";
        }
        String texte = DEBUTS_DE_FORMULE.indexOf(valeur.charAt(0)) >= 0 ? "'" + valeur : valeur;
        boolean aProteger = texte.contains(SEPARATEUR) || texte.contains("\"")
                || texte.contains("\n") || texte.contains("\r");
        return aProteger ? '"' + texte.replace("\"", "\"\"") + '"' : texte;
    }

    static String ligne(List<String> valeurs) {
        return valeurs.stream().map(Csv::champ).collect(Collectors.joining(SEPARATEUR)) + FIN_DE_LIGNE;
    }
}