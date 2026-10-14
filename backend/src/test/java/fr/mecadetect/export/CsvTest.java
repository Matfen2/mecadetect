package fr.mecadetect.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Écriture d'une ligne CSV pour Excel en français : séparateur « ; », guillemets si nécessaire. */
class CsvTest {

    @Test
    @DisplayName("Un champ contenant ; \" ou un retour à la ligne est entouré de guillemets, les guillemets doublés")
    void echappement() {
        assertThat(Csv.ligne(Arrays.asList("simple", "a;b", "il dit \"stop\"", "ligne 1\nligne 2", null)))
                .isEqualTo("simple;\"a;b\";\"il dit \"\"stop\"\"\";\"ligne 1\nligne 2\";\r\n");
    }

    @Test
    @DisplayName("Un champ qui commence par = + - @ est neutralisé : Excel ne l'exécute pas comme une formule")
    void injectionDeFormule() {
        assertThat(Csv.champ("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(Csv.champ("+33 6 00")).isEqualTo("'+33 6 00");
        assertThat(Csv.champ("-1")).isEqualTo("'-1");
        assertThat(Csv.champ("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(Csv.champ("Fuite d'huile")).isEqualTo("Fuite d'huile");
    }
}