package fr.mecadetect.erreur;

/// L'objet a été modifié par quelqu'un d'autre depuis que l'utilisateur l'a affiché (UC-DB6).
/// Traduite en HTTP 409 avec le code `CONFLIT_VERSION`, comme un conflit détecté par JPA.
public class ConflitVersionException extends RuntimeException {

    /// @param message détail du conflit, destiné aux journaux
    public ConflitVersionException(String message) {
        super(message);
    }
}
