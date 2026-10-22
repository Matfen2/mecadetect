package fr.mecadetect.dashboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// Paramétrage du tableau de bord, lu dans `application.yml` sous `mecadetect.tableau-de-bord`.
///
/// La valeur est vérifiée au démarrage : une taille hors bornes empêche l'application de démarrer
/// plutôt que de produire un tableau de bord incohérent.
///
/// @param tailleTop nombre d'équipements affichés dans le top des pannes (10 par défaut)
@Validated
@ConfigurationProperties("mecadetect.tableau-de-bord")
public record TableauDeBordProperties(

        @DefaultValue("10")
        @Min(1)
        @Max(50)
        int tailleTop) {
}