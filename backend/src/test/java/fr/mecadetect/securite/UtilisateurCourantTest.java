package fr.mecadetect.securite;

import fr.mecadetect.erreur.ActionNonAutoriseeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilisateurCourantTest {

    private final UtilisateurCourant utilisateurCourant = new UtilisateurCourant();

    @AfterEach
    void viderLeContexte() {
        SecurityContextHolder.clearContext();
    }

    private static void connecterAvecJeton(String sujet) {
        var jwt = Jwt.withTokenValue("jeton").header("alg", "HS256").subject(sujet).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void renvoie_l_identifiant_porte_par_le_jeton() {
        connecterAvecJeton("42");

        assertThat(utilisateurCourant.id()).isEqualTo(42L);
    }

    @Test
    void refuse_un_jeton_dont_le_sujet_n_est_pas_un_identifiant() {
        connecterAvecJeton("claire.martin@mecadetect.fr");

        assertThatThrownBy(utilisateurCourant::id).isInstanceOf(ActionNonAutoriseeException.class);
    }

    @Test
    void refuse_quand_personne_n_est_connecte() {
        assertThatThrownBy(utilisateurCourant::id).isInstanceOf(ActionNonAutoriseeException.class);
    }

    @Test
    void refuse_une_authentification_qui_n_est_pas_un_jeton_jwt() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("42", null, List.of()));

        assertThatThrownBy(utilisateurCourant::id).isInstanceOf(ActionNonAutoriseeException.class);
    }
}