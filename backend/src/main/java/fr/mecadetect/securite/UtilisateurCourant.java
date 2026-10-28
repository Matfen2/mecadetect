package fr.mecadetect.securite;

import fr.mecadetect.erreur.ActionNonAutoriseeException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/// Donne l'identifiant et les droits de l'utilisateur connecté, lus dans le jeton JWT.
@Component
public class UtilisateurCourant {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    /// Identifiant de l'utilisateur connecté, porté par la claim "sub" du jeton.
    ///
    /// @return l'identifiant de l'utilisateur
    /// @throws ActionNonAutoriseeException si aucun jeton exploitable n'accompagne la requête
    public Long id() {
        if (authentification() instanceof JwtAuthenticationToken jeton) {
            try {
                return Long.valueOf(jeton.getToken().getSubject());
            } catch (NumberFormatException e) {
                // Jeton signé par nous mais sans identifiant exploitable : on refuse
            }
        }
        throw new ActionNonAutoriseeException("UTILISATEUR_INCONNU", "Utilisateur connecté non reconnu.");
    }

    /// Indique si l'utilisateur connecté a le rôle ADMIN.
    ///
    /// @return vrai si le jeton porte le rôle ADMIN
    public boolean estAdmin() {
        Authentication authentification = authentification();
        return authentification != null && authentification.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }

    private static Authentication authentification() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
