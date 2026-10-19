package fr.mecadetect.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le profil prod ne doit hériter d'aucune valeur de développement : sans variable
 * d'environnement, l'application refuse de démarrer au lieu de signer les jetons
 * avec la clé publiée dans le dépôt ou de se connecter avec le mot de passe de dev.
 */
class ConfigurationProductionTest {

    /**
     * Environnement construit à la main, dans l'ordre de priorité de Spring Boot :
     * variables fournies, puis application-prod.yaml, puis application.yaml.
     * Les variables de la machine qui lance les tests n'interviennent pas.
     */
    private static StandardEnvironment environnementProduction(Map<String, Object> variables) throws IOException {
        var environnement = new StandardEnvironment();
        var sources = environnement.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("variables", variables));
        var chargeur = new YamlPropertySourceLoader();
        chargeur.load("prod", new ClassPathResource("application-prod.yaml")).forEach(sources::addLast);
        chargeur.load("commun", new ClassPathResource("application.yaml")).forEach(sources::addLast);
        return environnement;
    }

    @Test
    void sansJwtSecretLaCleDeDeveloppementNEstPasUtilisee() throws IOException {
        var environnement = environnementProduction(Map.of());

        assertThatThrownBy(() -> environnement.getProperty("mecadetect.securite.jwt.secret"))
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void sansVariablesLaBaseDeDeveloppementNEstPasUtilisee() throws IOException {
        var environnement = environnementProduction(Map.of());

        assertThatThrownBy(() -> environnement.getProperty("spring.datasource.url"))
                .hasMessageContaining("DB_URL");
        assertThatThrownBy(() -> environnement.getProperty("spring.datasource.username"))
                .hasMessageContaining("DB_USER");
        assertThatThrownBy(() -> environnement.getProperty("spring.datasource.password"))
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void lesVariablesFourniesSontReprises() throws IOException {
        var environnement = environnementProduction(Map.of(
                "JWT_SECRET", "c2VjcmV0LWRlLXRlc3Q=",
                "DB_URL", "jdbc:oracle:thin:@oracle:1521/FREEPDB1",
                "DB_USER", "mecadetect",
                "DB_PASSWORD", "mot-de-passe-de-test"));

        assertThat(environnement.getProperty("mecadetect.securite.jwt.secret")).isEqualTo("c2VjcmV0LWRlLXRlc3Q=");
        assertThat(environnement.getProperty("spring.datasource.url")).isEqualTo("jdbc:oracle:thin:@oracle:1521/FREEPDB1");
        assertThat(environnement.getProperty("spring.datasource.username")).isEqualTo("mecadetect");
        assertThat(environnement.getProperty("spring.datasource.password")).isEqualTo("mot-de-passe-de-test");
    }

    @Test
    void derriereLeProxyLesEntetesTransmisSontPrisEnCompte() throws IOException {
        var environnement = environnementProduction(Map.of());

        // Adresse réelle du client et schéma https, transmis par Caddy (X-Forwarded-*)
        assertThat(environnement.getProperty("server.forward-headers-strategy")).isEqualTo("native");
    }
}