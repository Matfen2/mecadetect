package fr.mecadetect.intervention.api;

import fr.mecadetect.erreur.DonneesInvalidesException;
import fr.mecadetect.intervention.domain.DemandePage;
import fr.mecadetect.intervention.domain.DemandePage.Tri;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tâche 4.4 : l'identifiant départage les ex aequo dans le sens du tri demandé.
/// Un tri « date décroissante puis identifiant croissant » mélange deux sens :
/// aucun index ne peut le fournir, Oracle trie alors toute la table pour une page de 20 lignes.
class PaginationInterventionTest {

    @Test
    @DisplayName("Par défaut : date de demande décroissante, puis identifiant décroissant")
    void triParDefaut() {
        DemandePage page = PaginationIntervention.versDemandePage(0, 20, null);

        assertThat(page.tri()).containsExactly(
                new Tri("dateDemande", false),
                new Tri("id", false));
    }

    @Test
    @DisplayName("Tri croissant demandé : l'identifiant départage aussi en croissant")
    void triCroissant() {
        DemandePage page = PaginationIntervention.versDemandePage(0, 20, "numero,asc");

        assertThat(page.tri()).containsExactly(
                new Tri("numero", true),
                new Tri("id", true));
    }

    @Test
    @DisplayName("Sans paramètres : première page de 20 éléments ; sans tri pour les listes à ordre fixe")
    void valeursParDefaut() {
        assertThat(PaginationIntervention.versDemandePage(null, null, null))
                .extracting(DemandePage::numero, DemandePage::taille)
                .containsExactly(0, PaginationIntervention.TAILLE_PAR_DEFAUT);
        assertThat(PaginationIntervention.sansTri(2, 10).tri()).isEmpty();
    }

    @Test
    @DisplayName("Un champ ou un sens de tri inconnu est refusé")
    void triInvalide() {
        assertThatThrownBy(() -> PaginationIntervention.versDemandePage(0, 20, "titre,asc"))
                .isInstanceOf(DonneesInvalidesException.class);
        assertThatThrownBy(() -> PaginationIntervention.versDemandePage(0, 20, "numero,haut"))
                .isInstanceOf(DonneesInvalidesException.class);
    }
}
