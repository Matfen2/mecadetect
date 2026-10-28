package fr.mecadetect.utilisateur;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Clé composée (type, technicien) : Hibernate s'appuie sur equals et hashCode pour
 * reconnaître une habilitation. Une erreur ici la ferait confondre ou dupliquer.
 */
class HabilitationIdTest {

    @Test
    void deux_cles_de_memes_composantes_sont_egales_et_de_meme_hash() {
        var cle = new HabilitationId(1L, 10L);
        var memeCle = new HabilitationId(1L, 10L);

        assertThat(cle).isEqualTo(memeCle).hasSameHashCodeAs(memeCle);
    }

    @Test
    void une_cle_est_egale_a_elle_meme() {
        var cle = new HabilitationId(1L, 10L);
        assertThat(cle).isEqualTo(cle);
    }

    @Test
    void un_type_different_donne_une_cle_differente() {
        assertThat(new HabilitationId(1L, 10L)).isNotEqualTo(new HabilitationId(2L, 10L));
    }

    @Test
    void un_technicien_different_donne_une_cle_differente() {
        assertThat(new HabilitationId(1L, 10L)).isNotEqualTo(new HabilitationId(1L, 11L));
    }

    @Test
    void les_composantes_ne_sont_pas_interchangeables() {
        assertThat(new HabilitationId(1L, 10L)).isNotEqualTo(new HabilitationId(10L, 1L));
    }

    @Test
    void une_cle_n_est_egale_ni_a_null_ni_a_un_autre_type() {
        assertThat(new HabilitationId(1L, 10L)).isNotEqualTo(null).isNotEqualTo("1-10");
    }

    @Test
    void un_ensemble_ne_garde_qu_une_fois_la_meme_habilitation() {
        var ensemble = new HashSet<HabilitationId>();
        ensemble.add(new HabilitationId(1L, 10L));
        ensemble.add(new HabilitationId(1L, 10L));
        ensemble.add(new HabilitationId(2L, 10L));

        assertThat(ensemble).hasSize(2);
    }

    @Test
    void expose_ses_composantes() {
        var cle = new HabilitationId(1L, 10L);

        assertThat(cle.getIdType()).isEqualTo(1L);
        assertThat(cle.getIdTechnicien()).isEqualTo(10L);
    }

    @Test
    void refuse_une_composante_absente() {
        assertThatNullPointerException().isThrownBy(() -> new HabilitationId(null, 10L));
        assertThatNullPointerException().isThrownBy(() -> new HabilitationId(1L, null));
    }
}