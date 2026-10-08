package fr.mecadetect.demo;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sans le profil "demo", aucun compte par défaut ne doit exister :
 * c'est une protection indispensable pour la production.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SansProfilDemoTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UtilisateurRepository repository;

    @Test
    @DisplayName("Sans profil demo, l'initialiseur n'est pas chargé et aucun compte de démo n'existe")
    void aucunCompteDeDemo() {
        assertThat(context.getBeansOfType(ComptesDemoInitialiseur.class)).isEmpty();
        assertThat(repository.existsByEmailIgnoreCase("admin@mecadetect.fr")).isFalse();
    }
}