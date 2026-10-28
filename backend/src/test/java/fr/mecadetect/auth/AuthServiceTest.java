package fr.mecadetect.auth;

import fr.mecadetect.securite.JwtService;
import fr.mecadetect.securite.JwtService.JetonGenere;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Test unitaire : aucune base, aucun contexte Spring.
///
/// Le repository et le service JWT sont simulés ; l'encodeur BCrypt est réel, déclaré en `@Spy`
/// pour que `@InjectMocks` le transmette au constructeur de [AuthService].
/// Le mode strict de [MockitoExtension] fait échouer un test qui déclare une simulation inutile.
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "alice@mecadetect.fr";
    private static final String MOT_DE_PASSE = "MotDePasse123";

    // Force BCrypt réduite (4) pour que les tests restent rapides
    @Spy private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    @Mock private UtilisateurRepository repository;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    private Utilisateur alice() {
        return new Utilisateur("Durand", "Alice", EMAIL, passwordEncoder.encode(MOT_DE_PASSE), Role.ADMIN);
    }

    @Test
    @DisplayName("Des identifiants valides renvoient un jeton et l'utilisateur")
    void connexionReussie() {
        Utilisateur alice = alice();
        when(repository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(alice));
        when(jwtService.generer(any())).thenReturn(new JetonGenere("jeton-de-test", 3600));

        AuthService.ResultatConnexion connexion = authService.connecter(EMAIL, MOT_DE_PASSE);

        assertThat(connexion.jeton().valeur()).isEqualTo("jeton-de-test");
        assertThat(connexion.utilisateur()).isSameAs(alice);
    }

    @Test
    @DisplayName("Un mot de passe incorrect est refusé")
    void motDePasseIncorrect() {
        // Créée avant le when() : alice() appelle le spy, ce qui interromprait la simulation en cours
        Utilisateur alice = alice();
        when(repository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> authService.connecter(EMAIL, "MauvaisMotDePasse"))
                .isInstanceOf(AuthentificationEchoueeException.class);
        verify(jwtService, never()).generer(any());
    }

    @Test
    @DisplayName("Un email inconnu est refusé avec la même exception (pas d'énumération des comptes)")
    void emailInconnu() {
        when(repository.findByEmailIgnoreCase("inconnu@mecadetect.fr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.connecter("inconnu@mecadetect.fr", MOT_DE_PASSE))
                .isInstanceOf(AuthentificationEchoueeException.class);
        verify(jwtService, never()).generer(any());
    }

    @Test
    @DisplayName("Un compte désactivé ne peut pas se connecter, même avec le bon mot de passe")
    void compteDesactive() {
        Utilisateur alice = alice();
        alice.desactiver();
        when(repository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> authService.connecter(EMAIL, MOT_DE_PASSE))
                .isInstanceOf(AuthentificationEchoueeException.class);
        verify(jwtService, never()).generer(any());
    }
}