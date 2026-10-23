package fr.mecadetect.demo;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.auth.AuthService;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avec le profil "demo", un compte par rôle est créé au démarrage.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class ComptesDemoTest {

    @Autowired
    private UtilisateurRepository repository;

    @Autowired
    private AuthService authService;

    @Autowired
    private ComptesDemoInitialiseur initialiseur;

    @Value("${mecadetect.demo.mot-de-passe}")
    private String motDePasseDemo;

    @Test
    @DisplayName("Un compte de démonstration existe pour chaque rôle")
    void unCompteParRole() {
        assertThat(repository.findByEmailIgnoreCase("admin@mecadetect.fr"))
                .get().extracting(Utilisateur::getRole).isEqualTo(Role.ADMIN);
        assertThat(repository.findByEmailIgnoreCase("technicien@mecadetect.fr"))
                .get().extracting(Utilisateur::getRole).isEqualTo(Role.TECHNICIEN);
        assertThat(repository.findByEmailIgnoreCase("demandeur@mecadetect.fr"))
                .get().extracting(Utilisateur::getRole).isEqualTo(Role.DEMANDEUR);
    }

    @Test
    @DisplayName("Le mot de passe de démonstration permet de se connecter")
    void connexionAvecLeMotDePasseDeDemo() {
        var resultat = authService.connecter("admin@mecadetect.fr", motDePasseDemo);

        assertThat(resultat.jeton().valeur()).isNotBlank();
    }

    @Test
    @DisplayName("Relancer l'initialisation ne crée pas de doublon (idempotence)")
    void initialisationIdempotente() {
        long avant = repository.count();

        initialiseur.run(null);

        assertThat(repository.count()).isEqualTo(avant);
    }
}