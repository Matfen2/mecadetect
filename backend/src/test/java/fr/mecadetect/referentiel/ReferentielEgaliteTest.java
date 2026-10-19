package fr.mecadetect.referentiel;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat d'égalité des entités du référentiel : identité par l'identifiant,
 * compatible avec les proxys Hibernate et stable avant et après l'enregistrement.
 */
class ReferentielEgaliteTest {

    private static <T> T avecId(T entite, long id) {
        ReflectionTestUtils.setField(entite, "id", id);
        return entite;
    }

    @Nested
    class ZoneEgalite {

        @Test
        void une_zone_est_egale_a_elle_meme_meme_sans_identifiant() {
            var zone = new Zone("A1", "Atelier presses");
            assertThat(zone).isEqualTo(zone);
        }

        @Test
        void deux_zones_de_meme_identifiant_sont_egales() {
            assertThat(avecId(new Zone("A1", "Atelier presses"), 1))
                    .isEqualTo(avecId(new Zone("A1", "Autre libellé"), 1))
                    .hasSameHashCodeAs(avecId(new Zone("B2", "Magasin"), 2));
        }

        @Test
        void deux_zones_d_identifiants_differents_sont_differentes() {
            assertThat(avecId(new Zone("A1", "Atelier"), 1)).isNotEqualTo(avecId(new Zone("A1", "Atelier"), 2));
        }

        @Test
        void deux_zones_non_enregistrees_sont_differentes_meme_identiques() {
            assertThat(new Zone("A1", "Atelier")).isNotEqualTo(new Zone("A1", "Atelier"));
        }

        @Test
        void une_zone_n_est_egale_ni_a_null_ni_a_un_autre_type() {
            var zone = avecId(new Zone("A1", "Atelier"), 1);
            assertThat(zone).isNotEqualTo(null).isNotEqualTo(avecId(new TypeEquipement("Presse"), 1));
        }

        @Test
        void une_zone_reste_retrouvable_dans_un_ensemble_apres_son_enregistrement() {
            var zone = new Zone("A1", "Atelier");
            var ensemble = new HashSet<Zone>();
            ensemble.add(zone);

            avecId(zone, 1);

            assertThat(ensemble).contains(zone);
        }
    }

    @Nested
    class TypeEquipementEgalite {

        @Test
        void un_type_est_egal_a_lui_meme_meme_sans_identifiant() {
            var type = new TypeEquipement("Presse");
            assertThat(type).isEqualTo(type);
        }

        @Test
        void deux_types_de_meme_identifiant_sont_egaux() {
            assertThat(avecId(new TypeEquipement("Presse"), 1))
                    .isEqualTo(avecId(new TypeEquipement("Presse hydraulique"), 1))
                    .hasSameHashCodeAs(avecId(new TypeEquipement("Tour CN"), 2));
        }

        @Test
        void deux_types_d_identifiants_differents_sont_differents() {
            assertThat(avecId(new TypeEquipement("Presse"), 1)).isNotEqualTo(avecId(new TypeEquipement("Presse"), 2));
        }

        @Test
        void deux_types_non_enregistres_sont_differents_meme_identiques() {
            assertThat(new TypeEquipement("Presse")).isNotEqualTo(new TypeEquipement("Presse"));
        }

        @Test
        void un_type_n_est_egal_ni_a_null_ni_a_un_autre_type() {
            var type = avecId(new TypeEquipement("Presse"), 1);
            assertThat(type).isNotEqualTo(null).isNotEqualTo(avecId(new Zone("A1", "Atelier"), 1));
        }
    }
}