package fr.mecadetect.erreur;

/// L'utilisateur est authentifié et a le bon rôle, mais n'a pas le droit d'agir
/// sur cette ressource précise : par exemple un technicien qui tente de démarrer
/// une intervention assignée à un collègue (RG-02). Réponse HTTP 403.
public class ActionNonAutoriseeException extends RuntimeException {

    private final String code;

    public ActionNonAutoriseeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}