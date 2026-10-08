package fr.mecadetect.auth;

/**
 * Levée quand l'email est inconnu, le mot de passe incorrect ou le compte désactivé.
 * Un seul message pour tous ces cas : on ne révèle pas quels comptes existent.
 */
public class AuthentificationEchoueeException extends RuntimeException {

    public AuthentificationEchoueeException() {
        super("Email ou mot de passe incorrect.");
    }
}