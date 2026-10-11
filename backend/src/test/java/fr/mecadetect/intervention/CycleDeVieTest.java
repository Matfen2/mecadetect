package fr.mecadetect.intervention;

import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.intervention.Transition.Annulation;
import fr.mecadetect.intervention.Transition.Cloture;
import fr.mecadetect.intervention.Transition.Demarrage;
import fr.mecadetect.intervention.Transition.Planification;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static fr.mecadetect.intervention.StatutIntervention.ANNULEE;
import static fr.mecadetect.intervention.StatutIntervention.DEMANDEE;
import static fr.mecadetect.intervention.StatutIntervention.EN_COURS;
import static fr.mecadetect.intervention.StatutIntervention.PLANIFIEE;
import static fr.mecadetect.intervention.StatutIntervention.TERMINEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de la machine à états, sans base ni Spring.
 * Chaque transition autorisée et chaque transition interdite est vérifiée.
 */
class CycleDeVieTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 10, 12, 9, 0);
    private static final Utilisateur TECHNICIEN =
            new Utilisateur("Bernard", "Lucas", "lucas@mecadetect.fr", "hash", Role.TECHNICIEN);

    private static final Transition PLANIFICATION = new Planification(TECHNICIEN, MAINTENANT);
    private static final Transition DEMARRAGE = new Demarrage(MAINTENANT);
    private static final Transition CLOTURE = new Cloture("Remplacement du joint", 45, MAINTENANT);
    private static final Transition ANNULATION = new Annulation("Doublon");

    /** Les 5 transitions autorisées : statut de départ, transition, statut d'arrivée. */
    private static final List<Arguments> AUTORISEES = List.of(
            Arguments.of(DEMANDEE, PLANIFICATION, PLANIFIEE),
            Arguments.of(PLANIFIEE, DEMARRAGE, EN_COURS),
            Arguments.of(EN_COURS, CLOTURE, TERMINEE),
            Arguments.of(DEMANDEE, ANNULATION, ANNULEE),
            Arguments.of(PLANIFIEE, ANNULATION, ANNULEE));

    static Stream<Arguments> transitionsAutorisees() {
        return AUTORISEES.stream();
    }

    /** Toutes les autres combinaisons (5 statuts x 4 transitions - 5 autorisées = 15). */
    static Stream<Arguments> transitionsInterdites() {
        return Arrays.stream(StatutIntervention.values())
                .flatMap(statut -> Stream.of(PLANIFICATION, DEMARRAGE, CLOTURE, ANNULATION)
                        .filter(transition -> AUTORISEES.stream().noneMatch(autorisee ->
                                autorisee.get()[0] == statut && autorisee.get()[1] == transition))
                        .map(transition -> Arguments.of(statut, transition)));
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("transitionsAutorisees")
    @DisplayName("Transition autorisée")
    void transitionAutorisee(StatutIntervention depart, Transition transition, StatutIntervention arrivee) {
        assertThat(CycleDeVie.statutApres(depart, transition)).isEqualTo(arrivee);
    }

    @ParameterizedTest(name = "{0} + {1} interdite")
    @MethodSource("transitionsInterdites")
    @DisplayName("Transition interdite")
    void transitionInterdite(StatutIntervention depart, Transition transition) {
        assertThatThrownBy(() -> CycleDeVie.statutApres(depart, transition))
                .isInstanceOf(RegleMetierException.class)
                .extracting("code")
                .isEqualTo("TRANSITION_INTERDITE");
    }

    @Test
    @DisplayName("RG-03 : une clôture sans compte rendu est refusée")
    void clotureSansCompteRendu() {
        assertThatThrownBy(() -> new Cloture("   ", 30, MAINTENANT))
                .isInstanceOf(RegleMetierException.class)
                .extracting("code")
                .isEqualTo("COMPTE_RENDU_OBLIGATOIRE");
    }

    @Test
    @DisplayName("RG-03 : une clôture avec un temps passé nul est refusée")
    void clotureSansTempsPasse() {
        assertThatThrownBy(() -> new Cloture("Remplacement du joint", 0, MAINTENANT))
                .isInstanceOf(RegleMetierException.class)
                .extracting("code")
                .isEqualTo("TEMPS_PASSE_INVALIDE");
    }

    @Test
    @DisplayName("RG-03 : une annulation sans motif est refusée")
    void annulationSansMotif() {
        assertThatThrownBy(() -> new Annulation(""))
                .isInstanceOf(RegleMetierException.class)
                .extracting("code")
                .isEqualTo("MOTIF_OBLIGATOIRE");
    }
}