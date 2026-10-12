package fr.mecadetect.securite;

import fr.mecadetect.erreur.ActionNonAutoriseeException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Donne l'identifiant de l'utilisateur connecté, porté par la claim "sub" du jeton. */
@Component
public class UtilisateurCourant {

    public Long id() {
        var authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification instanceof JwtAuthenticationToken jeton) {
            try {
                return Long.valueOf(jeton.getToken().getSubject());
            } catch (NumberFormatException e) {
                // Jeton signé par nous mais sans identifiant exploitable : on refuse
            }
        }
        throw new ActionNonAutoriseeException("UTILISATEUR_INCONNU", "Utilisateur connecté non reconnu.");
    }
}