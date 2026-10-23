package fr.mecadetect.intervention.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou découvert par le test de charge (tâche 4.8) : la recherche dynamique de l'étape 4
 * existait, mais InterventionJpaRepository ne l'utilisait pas et gardait l'ancienne requête JPQL
 * (:p is null or ...). Les tests fonctionnels passaient quand même : les deux versions renvoient
 * les mêmes résultats. Seuls les temps de réponse sous charge ont révélé l'écart.
 */
class RechercheDynamiqueBrancheeTest {

    @Test
    @DisplayName("InterventionJpaRepository délègue la recherche au fragment Criteria, sans requête JPQL concurrente")
    void rechercheParLeFragment() {
        assertThat(InterventionRechercheRepository.class).isAssignableFrom(InterventionJpaRepository.class);
        assertThat(Arrays.stream(InterventionJpaRepository.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("rechercher");
    }
}