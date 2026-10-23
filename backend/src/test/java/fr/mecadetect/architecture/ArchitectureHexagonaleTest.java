package fr.mecadetect.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Règles de dépendance de l'architecture hexagonale, vérifiées sur les sources des modules découpés.
///
/// - le **domaine** ne dépend ni des cas d'usage, ni de l'infrastructure, ni de l'API ;
/// - les **cas d'usage** (application) ne dépendent ni de l'infrastructure ni de l'API :
///   ils passent par les ports du domaine ;
/// - l'**infrastructure** ne dépend pas de l'API ;
/// - le **domaine** et les **cas d'usage** ne dépendent d'aucun framework (Spring, Lombok) :
///   une montée de version ou un changement de framework ne touche pas au code métier.
///   Seules les annotations JPA des entités sont tolérées dans le domaine, par choix pragmatique.
///
/// Le test lit les `import` des fichiers : une dépendance interdite fait échouer le build.
class ArchitectureHexagonaleTest {

    private static final Path SOURCES = Path.of("src/main/java/fr/mecadetect");

    /// Modules déjà découpés en domain / application / infrastructure / api.
    private static final List<String> MODULES = List.of("intervention");

    static Stream<Arguments> regles() {
        return MODULES.stream().flatMap(module -> Stream.of(
                Arguments.of(module, "domain", List.of("application", "infrastructure", "api")),
                Arguments.of(module, "application", List.of("infrastructure", "api")),
                Arguments.of(module, "infrastructure", List.of("api"))));
    }

    @ParameterizedTest(name = "{0}.{1} ne dépend pas de {2}")
    @MethodSource("regles")
    @DisplayName("Les dépendances vont de l'extérieur vers le domaine")
    void dependancesVersLeDomaine(String module, String couche, List<String> couchesInterdites) throws IOException {
        Path dossier = SOURCES.resolve(module).resolve(couche);
        assertThat(dossier).as("couche %s.%s", module, couche).isDirectory();

        Pattern importInterdit = Pattern.compile("^import (static )?fr\\.mecadetect\\." + module
                + "\\.(" + String.join("|", couchesInterdites) + ")\\.", Pattern.MULTILINE);

        try (Stream<Path> fichiers = Files.list(dossier)) {
            List<String> violations = fichiers
                    .filter(fichier -> fichier.toString().endsWith(".java"))
                    .filter(fichier -> importInterdit.matcher(lire(fichier)).find())
                    .map(fichier -> fichier.getFileName().toString())
                    .toList();

            assertThat(violations).as("fichiers de %s.%s avec une dépendance interdite", module, couche).isEmpty();
        }
    }

    static Stream<Arguments> couchesMetier() {
        return MODULES.stream().flatMap(module -> Stream.of(
                Arguments.of(module, "domain"),
                Arguments.of(module, "application")));
    }

    @ParameterizedTest(name = "{0}.{1} ne dépend d'aucun framework")
    @MethodSource("couchesMetier")
    @DisplayName("Le code métier est indépendant des frameworks")
    void codeMetierSansFramework(String module, String couche) throws IOException {
        Pattern importFramework = Pattern.compile("^import (static )?(org\\.springframework|lombok)\\.", Pattern.MULTILINE);

        try (Stream<Path> fichiers = Files.list(SOURCES.resolve(module).resolve(couche))) {
            List<String> violations = fichiers
                    .filter(fichier -> fichier.toString().endsWith(".java"))
                    .filter(fichier -> importFramework.matcher(lire(fichier)).find())
                    .map(fichier -> fichier.getFileName().toString())
                    .toList();

            assertThat(violations).as("fichiers de %s.%s qui dépendent d'un framework", module, couche).isEmpty();
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible : " + fichier, e);
        }
    }
}
