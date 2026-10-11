package fr.mecadetect.intervention;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.erreur.ActionNonAutoriseeException;
import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests unitaires de l'entité Intervention : règles appliquées sans base ni Spring. */
class InterventionTest {

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2026, 10, 12, 8, 0);

    private Equipement presse;
    private Utilisateur demandeur;
    private Utilisateur admin;
    private Utilisateur technicien;
    private Intervention intervention;

    @BeforeEach
    void setUp() {
        presse = new Equipement("PRS-001", "Presse 250 tonnes", LocalDate.of(2016, 3, 14),
                new TypeEquipement("Presse hydraulique"), new Zone("A1", "Atelier A"));
        demandeur = new Utilisateur("Moreau", "Sophie", "sophie@mecadetect.fr", "hash", Role.DEMANDEUR);
        admin = new Utilisateur("Martin", "Claire", "claire@mecadetect.fr", "hash", Role.ADMIN);
        technicien = new Utilisateur("Bernard", "Lucas", "lucas@mecadetect.fr", "hash", Role.TECHNICIEN);
        intervention = new Intervention("INT-2026-000001", "Fuite d'huile", "Fuite sur le vérin principal",
                Priorite.HAUTE, presse, demandeur, LUNDI_8H);
    }

    @Test
    @DisplayName("Une panne déclarée crée une intervention DEMANDEE, sans technicien")
    void declaration() {
        assertThat(intervention.getStatut()).isEqualTo(StatutIntervention.DEMANDEE);
        assertThat(intervention.getDateDemande()).isEqualTo(LUNDI_8H);
        assertThat(intervention.getTechnicien()).isNull();
        assertThat(intervention.getModifiePar()).isSameAs(demandeur);
        assertThat(intervention.estOuverte()).isTrue();
    }

    @Test
    @DisplayName("RG-07 : un équipement archivé ne peut plus recevoir d'intervention")
    void equipementArchive() {
        presse.archiver();

        assertThatThrownBy(() -> new Intervention("INT-2026-000002", "Bruit", "Bruit anormal au démarrage",
                Priorite.NORMALE, presse, demandeur, LUNDI_8H))
                .isInstanceOf(RegleMetierException.class)
                .extracting("code")
                .isEqualTo("EQUIPEMENT_ARCHIVE");
    }

    @Test
    @DisplayName("La planification assigne le technicien et trace l'auteur")
    void planification() {
        intervention.planifier(technicien, LUNDI_8H.plusDays(1), admin);

        assertThat(intervention.getStatut()).isEqualTo(StatutIntervention.PLANIFIEE);
        assertThat(intervention.getTechnicien()).isSameAs(technicien);
        assertThat(intervention.getDatePlanifiee()).isEqualTo(LUNDI_8H.plusDays(1));
        assertThat(intervention.getModifiePar()).isSameAs(admin);
    }

    @Test
    @DisplayName("Une intervention ne peut être assignée qu'à un technicien")
    void planificationSurUnNonTechnicien() {
        assertThatThrownBy(() -> intervention.planifier(demandeur, LUNDI_8H.plusDays(1), admin))
                .isInstanceOf(RegleMetierException.class)
                .extracting("code")
                .isEqualTo("TECHNICIEN_INVALIDE");
        assertThat(intervention.getStatut()).isEqualTo(StatutIntervention.DEMANDEE);
    }

    @Test
    @DisplayName("RG-02 : un autre technicien que l'assigné ne peut pas démarrer l'intervention")
    void demarrageParUnAutreTechnicien() {
        var collegue = new Utilisateur("Petit", "Hugo", "hugo@mecadetect.fr", "hash", Role.TECHNICIEN);
        intervention.planifier(technicien, LUNDI_8H.plusDays(1), admin);

        assertThatThrownBy(() -> intervention.demarrer(collegue, LUNDI_8H.plusDays(1)))
                .isInstanceOf(ActionNonAutoriseeException.class)
                .extracting("code")
                .isEqualTo("TECHNICIEN_NON_ASSIGNE");
        assertThat(intervention.getStatut()).isEqualTo(StatutIntervention.PLANIFIEE);
    }

    @Test
    @DisplayName("Cycle complet : planification, démarrage puis clôture par le technicien")
    void cycleComplet() {
        var debut = LUNDI_8H.plusDays(1);
        var fin = debut.plusMinutes(90);

        intervention.planifier(technicien, debut, admin);
        intervention.demarrer(technicien, debut);
        intervention.cloturer("  Joint du vérin remplacé, essai OK  ", 90, technicien, fin);

        assertThat(intervention.getStatut()).isEqualTo(StatutIntervention.TERMINEE);
        assertThat(intervention.getDateDebut()).isEqualTo(debut);
        assertThat(intervention.getDateFin()).isEqualTo(fin);
        assertThat(intervention.getTempsPasseMin()).isEqualTo(90);
        assertThat(intervention.getCompteRendu()).isEqualTo("Joint du vérin remplacé, essai OK");
        assertThat(intervention.getModifiePar()).isSameAs(technicien);
        assertThat(intervention.estOuverte()).isFalse();
    }

    @Test
    @DisplayName("Une intervention planifiée peut être annulée avec un motif")
    void annulation() {
        intervention.planifier(technicien, LUNDI_8H.plusDays(1), admin);

        intervention.annuler("  Panne résolue par l'opérateur  ", admin);

        assertThat(intervention.getStatut()).isEqualTo(StatutIntervention.ANNULEE);
        assertThat(intervention.getMotifAnnulation()).isEqualTo("Panne résolue par l'opérateur");
        assertThat(intervention.getModifiePar()).isSameAs(admin);
    }

    @Test
    @DisplayName("Une intervention terminée ne peut plus être annulée")
    void annulationApresCloture() {
        intervention.planifier(technicien, LUNDI_8H, admin);
        intervention.demarrer(technicien, LUNDI_8H);
        intervention.cloturer("Joint remplacé", 30, technicien, LUNDI_8H.plusMinutes(30));

        assertThatThrownBy(() -> intervention.annuler("Erreur de saisie", admin))
                .isInstanceOf(RegleMetierException.class)
                .extracting("code")
                .isEqualTo("TRANSITION_INTERDITE");
        assertThat(intervention.getStatut()).isEqualTo(StatutIntervention.TERMINEE);
        assertThat(intervention.getMotifAnnulation()).isNull();
    }
}