package fr.mecadetect.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Écriture CSV pour Excel en français (séparateur « ; », guillemets si nécessaire) et envoi en flux.
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
        assertThat(Csv.ligne(List.of("=HYPERLINK(\"http://x\")"))).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"\r\n");
        assertThat(Csv.ligne(List.of("+33 6 00", "-1", "@SUM(A1)", "Fuite d'huile")))
                .isEqualTo("'+33 6 00;'-1;'@SUM(A1);Fuite d'huile\r\n");
    }

    @Test
    @DisplayName("Le flux restitue toutes les lignes, en UTF-8")
    void fluxComplet() throws IOException {
        try (InputStream flux = Csv.enFlux(Stream.of("Numéro;Titre\r\n", "INT-1;Fuite\r\n"))) {
            assertThat(new String(flux.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("Numéro;Titre\r\nINT-1;Fuite\r\n");
        }
    }

    @Test
    @DisplayName("Un téléchargement interrompu ferme la source sans lire les lignes restantes")
    void fermetureAnticipee() throws IOException {
        var produites = new AtomicInteger();
        var sourceFermee = new AtomicBoolean();
        Stream<String> lignes = Stream.iterate(1, i -> i + 1)
                .limit(1_000_000)
                .map(i -> {
                    produites.incrementAndGet();
                    return "INT-" + i + "\r\n";
                })
                .onClose(() -> sourceFermee.set(true));

        InputStream flux = Csv.enFlux(lignes);
        flux.read(new byte[4]);
        flux.close();

        assertThat(sourceFermee).isTrue();
        assertThat(produites.get()).isLessThan(10);
    }
}
