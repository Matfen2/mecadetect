package fr.mecadetect.intervention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tâche 4.4 : l'identifiant départage les ex aequo dans le sens du tri demandé.
 * Un tri « date décroissante puis identifiant croissant » mélange deux sens :
 * aucun index ne peut le fournir, Oracle trie alors toute la table pour une page de 20 lignes.
 */
class PaginationInterventionTest {

    @Test
    @DisplayName("Par défaut : date de demande décroissante, puis identifiant décroissant")
    void triParDefaut() {
        var sort = PaginationIntervention.versPageable(0, 20, null).getSort();

        assertThat(sort).containsExactly(
                Sort.Order.desc("dateDemande"),
                Sort.Order.desc("id"));
    }

    @Test
    @DisplayName("Tri croissant demandé : l'identifiant départage aussi en croissant")
    void triCroissant() {
        var sort = PaginationIntervention.versPageable(0, 20, "numero,asc").getSort();

        assertThat(sort).containsExactly(
                Sort.Order.asc("numero"),
                Sort.Order.asc("id"));
    }
}