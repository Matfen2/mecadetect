package fr.mecadetect.erreur;

/**
 * Donnée envoyée par le client incohérente avec la base (zone ou type inexistant...).
 * Différent du 404 : la ressource de l'URL existe, c'est le contenu de la requête qui est faux.
 */
public class DonneesInvalidesException extends RuntimeException {

    private final String code;

    public DonneesInvalidesException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}