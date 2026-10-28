package fr.mecadetect.dashboard;

import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.reporting.TempsResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Le temps moyen global pondère chaque priorité par son nombre d'interventions. */
class MoyennePondereeTest {

    @Test
    @DisplayName("Moyenne pondérée par le nombre d'interventions, arrondie au dixième ; 0 sans intervention")
    void moyennePonderee() {
        var temps = List.of(
                new TempsResolution(Priorite.CRITIQUE, 1, 2.0),
                new TempsResolution(Priorite.BASSE, 3, 6.0));

        assertThat(DashboardService.tempsMoyenGlobal(temps)).isEqualTo(5.0);
        assertThat(DashboardService.tempsMoyenGlobal(List.of(
                new TempsResolution(Priorite.HAUTE, 2, 1.25),
                new TempsResolution(Priorite.NORMALE, 1, 1.0)))).isEqualTo(1.2);
        assertThat(DashboardService.tempsMoyenGlobal(List.of())).isZero();
    }
}