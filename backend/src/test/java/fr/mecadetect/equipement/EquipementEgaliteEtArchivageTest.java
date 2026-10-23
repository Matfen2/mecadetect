package fr.mecadetect.equipement;

import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.Zone;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipementEgaliteEtArchivageTest {

    private static Equipement equipement() {
        return new Equipement("PRS-001", "Presse 225 tonnes", LocalDate.of(2020, 3, 1),
                new TypeEquipement("Presse"), new Zone("A1", "Atelier presses"));
    }

    private static Equipement avecId(long id) {
        var equipement = equipement();
        ReflectionTestUtils.setField(equipement, "id", id);
        return equipement;
    }

    private static Equipement archive() {
        var equipement = equipement();
        equipement.archiver();
        return equipement;
    }

    // ------------------------------------------------------------------ Égalité

    @Test
    void deux_equipements_de_meme_identifiant_sont_egaux() {
        assertThat(avecId(1)).isEqualTo(avecId(1)).hasSameHashCodeAs(avecId(2));
    }

    @Test
    void deux_equipements_d_identifiants_differents_sont_differents() {
        assertThat(avecId(1)).isNotEqualTo(avecId(2)).isNotEqualTo(null).isNotEqualTo("PRS-001");
    }

    @Test
    void un_equipement_non_enregistre_n_est_egal_qu_a_lui_meme() {
        var nouveau = equipement();

        assertThat(nouveau).isEqualTo(nouveau).isNotEqualTo(equipement()).isNotEqualTo(avecId(1));
    }

    @Test
    void un_equipement_reste_retrouvable_dans_un_ensemble_apres_son_enregistrement() {
        var equipement = equipement();
        var ensemble = new HashSet<Equipement>();
        ensemble.add(equipement);

        ReflectionTestUtils.setField(equipement, "id", 1L);

        assertThat(ensemble).contains(equipement);
    }

    // ------------------------------------------------------------------ Équipement archivé

    @Test
    void un_equipement_archive_ne_peut_plus_recevoir_de_panne() {
        var equipement = archive();

        assertThatThrownBy(equipement::signalerPanne).isInstanceOf(RegleMetierException.class);
        assertThat(equipement.getStatut()).isEqualTo(StatutEquipement.ARCHIVE);
    }

    @Test
    void le_statut_d_un_equipement_archive_ne_suit_plus_les_interventions() {
        var equipement = archive();

        equipement.recalculerStatut(true, true);

        assertThat(equipement.getStatut()).isEqualTo(StatutEquipement.ARCHIVE);
    }
}