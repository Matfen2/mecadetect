package fr.mecadetect.equipement.importation;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.StatutEquipement;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Validation d'une ligne du fichier d'import : test unitaire, sans base ni Spring Batch.
class ValidationLigneEquipementTest {

    private static final LocalDate AUJOURD_HUI = LocalDate.of(2026, 10, 27);

    private final TypeEquipement presse = new TypeEquipement("Presse hydraulique");
    private final Zone atelierA = new Zone("ATA", "Atelier A");

    private ValidationLigneEquipement validation;

    @BeforeEach
    void setUp() {
        // PRS-001 existe déjà en base
        Set<String> referencesEnBase = Set.of("prs-001");
        validation = new ValidationLigneEquipement(
                List.of(presse), List.of(atelierA),
                reference -> referencesEnBase.contains(reference.toLowerCase()),
                AUJOURD_HUI);
    }

    private static LigneEquipementCsv ligne(int numero, String reference, String nom, String date, String type, String zone) {
        return new LigneEquipementCsv(numero, reference, nom, date, type, zone);
    }

    @Test
    @DisplayName("Une ligne valide donne un équipement en service, avec son type et sa zone")
    void ligneValide() {
        Equipement equipement = validation.valider(
                ligne(2, " PRS-100 ", " Presse 200 t ", "2022-01-10", "Presse hydraulique", "ATA"));

        assertThat(equipement.getReference()).isEqualTo("PRS-100");
        assertThat(equipement.getNom()).isEqualTo("Presse 200 t");
        assertThat(equipement.getDateMiseService()).isEqualTo(LocalDate.of(2022, 1, 10));
        assertThat(equipement.getStatut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(equipement.getType()).isSameAs(presse);
        assertThat(equipement.getZone()).isSameAs(atelierA);
    }

    @Test
    @DisplayName("Le type et la zone sont reconnus sans tenir compte de la casse ni des espaces")
    void typeEtZoneSansCasse() {
        Equipement equipement = validation.valider(
                ligne(2, "PRS-101", "Presse", "2022-01-10", "  presse HYDRAULIQUE ", " ata "));

        assertThat(equipement.getType()).isSameAs(presse);
        assertThat(equipement.getZone()).isSameAs(atelierA);
    }

    @Test
    @DisplayName("La date est acceptée au format JJ/MM/AAAA, celui d'Excel en français")
    void dateAuFormatFrancais() {
        Equipement equipement = validation.valider(
                ligne(2, "PRS-102", "Presse", "10/01/2022", "Presse hydraulique", "ATA"));

        assertThat(equipement.getDateMiseService()).isEqualTo(LocalDate.of(2022, 1, 10));
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Une ligne invalide est rejetée avec son numéro et un motif lisible")
    @CsvSource(delimiter = '|', textBlock = """
            Référence vide          | ''        | Presse | 2022-01-10 | Presse hydraulique | ATA | La référence est obligatoire.
            Référence trop longue   | ABCDEFGHIJKLMNOPQRSTUVWXYZ12345 | Presse | 2022-01-10 | Presse hydraulique | ATA | La référence dépasse 30 caractères.
            Nom vide                | PRS-103   | ''     | 2022-01-10 | Presse hydraulique | ATA | Le nom est obligatoire.
            Date illisible          | PRS-104   | Presse | 2022-13-45 | Presse hydraulique | ATA | La date de mise en service « 2022-13-45 » est invalide (formats acceptés : AAAA-MM-JJ ou JJ/MM/AAAA).
            Date dans le futur      | PRS-105   | Presse | 2027-01-01 | Presse hydraulique | ATA | La date de mise en service ne peut pas être dans le futur.
            Type inconnu            | PRS-106   | Presse | 2022-01-10 | Tour CN            | ATA | Le type d'équipement « Tour CN » n'existe pas.
            Zone inconnue           | PRS-107   | Presse | 2022-01-10 | Presse hydraulique | ZZZ | La zone « ZZZ » n'existe pas.
            Référence déjà en base  | prs-001   | Presse | 2022-01-10 | Presse hydraulique | ATA | La référence « prs-001 » est déjà utilisée par un équipement existant.
            """)
    void ligneInvalide(String cas, String reference, String nom, String date, String type, String zone, String motif) {
        assertThatThrownBy(() -> validation.valider(ligne(7, reference, nom, date, type, zone)))
                .isInstanceOf(LigneRejeteeException.class)
                .hasMessage(motif)
                .extracting(e -> ((LigneRejeteeException) e).getNumeroLigne())
                .isEqualTo(7);
    }

    @Test
    @DisplayName("Une référence présente deux fois dans le fichier : la seconde ligne est rejetée")
    void doublonDansLeFichier() {
        validation.valider(ligne(2, "PRS-200", "Presse", "2022-01-10", "Presse hydraulique", "ATA"));

        assertThatThrownBy(() -> validation.valider(
                ligne(9, "prs-200", "Autre presse", "2022-01-10", "Presse hydraulique", "ATA")))
                .isInstanceOf(LigneRejeteeException.class)
                .hasMessage("La référence « prs-200 » apparaît déjà à la ligne 2 du fichier.");
    }

    @Test
    @DisplayName("Une même ligne traitée deux fois (relecture d'un lot après une erreur) n'est pas un doublon")
    void memeLigneRetraitee() {
        var premiere = ligne(2, "PRS-300", "Presse", "2022-01-10", "Presse hydraulique", "ATA");
        validation.valider(premiere);

        assertThat(validation.valider(premiere).getReference()).isEqualTo("PRS-300");
    }
}
