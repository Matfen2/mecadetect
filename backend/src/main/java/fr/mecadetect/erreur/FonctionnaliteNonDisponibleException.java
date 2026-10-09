package fr.mecadetect.erreur;

/**
 * Endpoint présent dans le contrat OpenAPI mais pas encore implémenté (HTTP 501).
 * Permet d'implémenter une interface générée progressivement, sprint après sprint.
 */
public class FonctionnaliteNonDisponibleException extends RuntimeException {

    public FonctionnaliteNonDisponibleException(String fonctionnalite) {
        super("Fonctionnalité pas encore disponible : " + fonctionnalite);
    }
}