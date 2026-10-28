package fr.mecadetect.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/// Paramétrage du tableau de bord : valeur par défaut, lecture de la configuration et bornes.
///
/// Le contexte ne contient que la classe de propriétés : le test ne démarre ni la base ni le web.
class TableauDeBordPropertiesTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurationDeTest.class);

    @EnableConfigurationProperties(TableauDeBordProperties.class)
    static class ConfigurationDeTest {
    }

    @Test
    @DisplayName("Sans configuration, le top affiche 10 équipements")
    void valeurParDefaut() {
        contexte.run(ctx -> assertThat(ctx.getBean(TableauDeBordProperties.class).tailleTop()).isEqualTo(10));
    }

    @Test
    @DisplayName("La taille du top est lue dans la configuration")
    void valeurConfiguree() {
        contexte.withPropertyValues("mecadetect.tableau-de-bord.taille-top=5")
                .run(ctx -> assertThat(ctx.getBean(TableauDeBordProperties.class).tailleTop()).isEqualTo(5));
    }

    @Test
    @DisplayName("Une taille de 0 empêche le démarrage")
    void tailleNulleRefusee() {
        contexte.withPropertyValues("mecadetect.tableau-de-bord.taille-top=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("Une taille supérieure à 50 empêche le démarrage")
    void tailleTropGrandeRefusee() {
        contexte.withPropertyValues("mecadetect.tableau-de-bord.taille-top=51")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}