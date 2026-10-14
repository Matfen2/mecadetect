package fr.mecadetect.intervention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tâche 4.8 : la page de recherche indique à Oracle combien de lignes elle attend (FIRST_ROWS),
 * pour qu'il lise l'index trié au lieu de parcourir et trier toute la table.
 * Les tests de recherche existants (RechercheInterventionTest) vérifient en plus, sur Oracle,
 * que la requête avec l'indication reste valide et renvoie les mêmes résultats.
 */
class IndicationPremieresLignesTest {

    @Test
    @DisplayName("L'indication porte la taille de page, et ne dépend pas du numéro de page")
    void tailleDePage() {
        assertThat(InterventionRechercheRepositoryImpl.indicationPremieresLignes(PageRequest.of(0, 20)))
                .isEqualTo("FIRST_ROWS(20)");
        assertThat(InterventionRechercheRepositoryImpl.indicationPremieresLignes(PageRequest.of(49, 20)))
                .isEqualTo("FIRST_ROWS(20)");
        assertThat(InterventionRechercheRepositoryImpl.indicationPremieresLignes(PageRequest.of(0, 10)))
                .isEqualTo("FIRST_ROWS(10)");
    }
}