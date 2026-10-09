package fr.mecadetect.erreur;

/** Ressource demandée dans l'URL et absente (HTTP 404). */
public class RessourceIntrouvableException extends RuntimeException {

    private final String code;

    public RessourceIntrouvableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}