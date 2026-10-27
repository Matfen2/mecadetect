package fr.mecadetect.equipement.importation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Découpage d'une ligne du fichier CSV en colonnes, avant toute validation.
class LecteurLigneEquipementTest {

    private final LecteurLigneEquipement lecteur = new LecteurLigneEquipement();

    @Test
    @DisplayName("Les 5 colonnes séparées par des points-virgules sont lues, avec le numéro de ligne")
    void lecture() throws Exception {
        LigneEquipementCsv ligne = lecteur.mapLine("PRS-100;Presse 200 t;2022-01-10;Presse hydraulique;ATA", 4);

        assertThat(ligne).isEqualTo(
                new LigneEquipementCsv(4, "PRS-100", "Presse 200 t", "2022-01-10", "Presse hydraulique", "ATA"));
    }

    @Test
    @DisplayName("Un nom entre guillemets peut contenir un point-virgule")
    void champEntreGuillemets() throws Exception {
        LigneEquipementCsv ligne = lecteur.mapLine("PRS-101;\"Presse; 200 t\";2022-01-10;Presse hydraulique;ATA", 5);

        assertThat(ligne.nom()).isEqualTo("Presse; 200 t");
    }

    @Test
    @DisplayName("Une ligne qui n'a pas exactement 5 colonnes est illisible")
    void nombreDeColonnesIncorrect() {
        assertThatThrownBy(() -> lecteur.mapLine("PRS-102;Presse;2022-01-10", 6))
                .isInstanceOf(RuntimeException.class);
    }
}
