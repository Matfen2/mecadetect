package fr.mecadetect.auth;

import fr.mecadetect.securite.JwtService;
import fr.mecadetect.securite.JwtService.JetonGenere;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitaire : aucune base, aucun contexte Spring.
 * Le repository et le service JWT sont simulés avec Mockito.
 */
class AuthServiceTest {

    // Force BCrypt réduite (4) pour que les tests restent rapides
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final UtilisateurRepository repository = mock(UtilisateurRepository.class);
    private final JwtService jwtService = mock(JwtService.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(repository, passwordEncoder, jwtService);
        when(jwtService.generer(any())).thenReturn(new JetonGenere("jeton-de-test", 3600));
    }

    private Utilisateur utilisateur(String motDePasse) {
        return new Utilisateur("Durand", "Alice", "alice@mecadetect.fr", passwordEncoder.encode(motDePasse), Role.ADMIN);
    }

    @Test
    @DisplayName("Des identifiants valides renvoient un jeton et l'utilisateur")
    void connexionReussie() {
        var alice = utilisateur("MotDePasse123");
        when(repository.findByEmailIgnoreCase("alice@mecadetect.fr")).thenReturn(Optional.of(alice));

        var resultat = authService.connecter("alice@mecadetect.fr", "MotDePasse123");

        assertThat(resultat.jeton().valeur()).isEqualTo("jeton-de-test");
        assertThat(resultat.utilisateur()).isSameAs(alice);
    }

    @Test
    @DisplayName("Un mot de passe incorrect est refusé")
    void motDePasseIncorrect() {
        when(repository.findByEmailIgnoreCase("alice@mecadetect.fr")).thenReturn(Optional.of(utilisateur("MotDePasse123")));

        assertThatThrownBy(() -> authService.connecter("alice@mecadetect.fr", "MauvaisMotDePasse"))
                .isInstanceOf(AuthentificationEchoueeException.class);
        verify(jwtService, never()).generer(any());
    }

    @Test
    @DisplayName("Un email inconnu est refusé avec la même exception (pas d'énumération des comptes)")
    void emailInconnu() {
        when(repository.findByEmailIgnoreCase("inconnu@mecadetect.fr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.connecter("inconnu@mecadetect.fr", "MotDePasse123"))
                .isInstanceOf(AuthentificationEchoueeException.class);
    }

    @Test
    @DisplayName("Un compte désactivé ne peut pas se connecter, même avec le bon mot de passe")
    void compteDesactive() {
        var alice = utilisateur("MotDePasse123");
        alice.desactiver();
        when(repository.findByEmailIgnoreCase("alice@mecadetect.fr")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> authService.connecter("alice@mecadetect.fr", "MotDePasse123"))
                .isInstanceOf(AuthentificationEchoueeException.class);
        verify(jwtService, never()).generer(any());
    }
}