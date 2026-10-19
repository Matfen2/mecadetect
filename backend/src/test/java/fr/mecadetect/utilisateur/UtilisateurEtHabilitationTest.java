package fr.mecadetect.utilisateur;

import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.referentiel.TypeEquipement;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilisateurEtHabilitationTest {

    private static <T> T avecId(T entite, long id) {
        ReflectionTestUtils.setField(entite, "id", id);
        return entite;
    }

    private static Utilisateur utilisateur(Role role) {
        return new Utilisateur("Martin", "Claire", "Claire.Martin@MecaDetect.fr", "{bcrypt}hash", role);
    }

    // ------------------------------------------------------------------ Utilisateur

    @Test
    void le_nom_complet_est_prenom_puis_nom() {
        assertThat(utilisateur(Role.ADMIN).getNomComplet()).isEqualTo("Claire Martin");
    }

    @Test
    void l_email_est_enregistre_en_minuscules() {
        assertThat(utilisateur(Role.ADMIN).getEmail()).isEqualTo("claire.martin@mecadetect.fr");
    }

    @Test
    void un_compte_desactive_peut_etre_reactive() {
        var compte = utilisateur(Role.DEMANDEUR);

        compte.desactiver();
        assertThat(compte.isActif()).isFalse();

        compte.reactiver();
        assertThat(compte.isActif()).isTrue();
    }

    @Test
    void deux_utilisateurs_de_meme_identifiant_sont_egaux() {
        assertThat(avecId(utilisateur(Role.ADMIN), 1))
                .isEqualTo(avecId(utilisateur(Role.TECHNICIEN), 1))
                .hasSameHashCodeAs(avecId(utilisateur(Role.DEMANDEUR), 2));
    }

    @Test
    void un_utilisateur_non_enregistre_n_est_egal_qu_a_lui_meme() {
        var nouveau = utilisateur(Role.ADMIN);

        assertThat(nouveau).isEqualTo(nouveau).isNotEqualTo(utilisateur(Role.ADMIN));
        assertThat(nouveau).isNotEqualTo(avecId(utilisateur(Role.ADMIN), 1));
    }

    @Test
    void deux_utilisateurs_d_identifiants_differents_sont_differents() {
        assertThat(avecId(utilisateur(Role.ADMIN), 1))
                .isNotEqualTo(avecId(utilisateur(Role.ADMIN), 2))
                .isNotEqualTo(null)
                .isNotEqualTo("Claire Martin");
    }

    // ------------------------------------------------------------------ Habilitation (RG-09)

    @Test
    void un_technicien_recoit_une_habilitation_identifiee_par_le_type_et_le_technicien() {
        var type = avecId(new TypeEquipement("Presse"), 3);
        var technicien = avecId(utilisateur(Role.TECHNICIEN), 7);
        var date = LocalDate.of(2026, 1, 15);

        var habilitation = new Habilitation(type, technicien, date);

        assertThat(habilitation.getId()).isEqualTo(new HabilitationId(3L, 7L));
        assertThat(habilitation.getType()).isSameAs(type);
        assertThat(habilitation.getTechnicien()).isSameAs(technicien);
        assertThat(habilitation.getDateHabilitation()).isEqualTo(date);
    }

    @Test
    void seul_un_technicien_peut_recevoir_une_habilitation() {
        var type = avecId(new TypeEquipement("Presse"), 3);
        var demandeur = avecId(utilisateur(Role.DEMANDEUR), 8);

        assertThatThrownBy(() -> new Habilitation(type, demandeur, LocalDate.of(2026, 1, 15)))
                .isInstanceOf(RegleMetierException.class);
    }
}