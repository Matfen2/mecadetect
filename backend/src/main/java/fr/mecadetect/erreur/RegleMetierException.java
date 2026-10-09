package fr.mecadetect.erreur;

/**
 * Violation d'une règle métier (transition interdite, équipement archivé...).
 * Le code est transmis au front dans la réponse d'erreur (HTTP 409).
 */
public class RegleMetierException extends RuntimeException {

    private final String code;

    public RegleMetierException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}