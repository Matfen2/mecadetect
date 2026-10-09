package fr.mecadetect.equipement;

import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.Zone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test unitaire : les règles de l'entité sont vérifiées sans base de données.
 */
class EquipementTest {

    private final Zone atelierA = new Zone("A1", "Atelier A - Presses");
    private final Zone atelierB = new Zone("B1", "Atelier B - Usinage");
    private final TypeEquipement presse = new TypeEquipement("Presse hydraulique");
    private final TypeEquipement tour = new TypeEquipement("Tour CN");

    private Equipement nouvelEquipement() {
        return new Equipement("PRS-001", "Presse 250 tonnes", LocalDate.of(2020, 3, 15), presse, atelierA);
    }

    @Test
    @DisplayName("Un nouvel équipement est en service")
    void nouvelEquipementEnService() {
        assertThat(nouvelEquipement().getStatut()).isEqualTo(StatutEquipement.EN_SERVICE);
    }

    @Test
    @DisplayName("La modification met à jour toutes les informations")
    void modification() {
        var equipement = nouvelEquipement();

        equipement.modifier("TCN-002", "Tour numérique", LocalDate.of(2021, 6, 1), tour, atelierB);

        assertThat(equipement.getReference()).isEqualTo("TCN-002");
        assertThat(equipement.getNom()).isEqualTo("Tour numérique");
        assertThat(equipement.getType()).isSameAs(tour);
        assertThat(equipement.getZone()).isSameAs(atelierB);
    }

    @Test
    @DisplayName("L'archivage passe l'équipement au statut ARCHIVE")
    void archivage() {
        var equipement = nouvelEquipement();

        equipement.archiver();

        assertThat(equipement.getStatut()).isEqualTo(StatutEquipement.ARCHIVE);
        assertThat(equipement.estArchive()).isTrue();
    }

    @Test
    @DisplayName("Un équipement déjà archivé ne peut pas être archivé à nouveau")
    void doubleArchivageInterdit() {
        var equipement = nouvelEquipement();
        equipement.archiver();

        assertThatThrownBy(equipement::archiver)
                .isInstanceOf(RegleMetierException.class)
                .extracting("code").isEqualTo("EQUIPEMENT_DEJA_ARCHIVE");
    }

    @Test
    @DisplayName("Un équipement archivé ne peut plus être modifié")
    void modificationArchiveInterdite() {
        var equipement = nouvelEquipement();
        equipement.archiver();

        assertThatThrownBy(() -> equipement.modifier("X", "X", LocalDate.now(), tour, atelierB))
                .isInstanceOf(RegleMetierException.class)
                .extracting("code").isEqualTo("EQUIPEMENT_ARCHIVE");
    }
}