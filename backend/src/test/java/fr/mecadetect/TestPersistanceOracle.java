package fr.mecadetect;

import fr.mecadetect.maintenance.ArchivageRepository;
import fr.mecadetect.reporting.ReportingRepository;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Configuration commune des tests de la couche de persistance, sur une vraie base Oracle (Testcontainers).
///
/// - `@DataJpaTest` ne charge que JPA, les repositories Spring Data, `JdbcTemplate` et Flyway :
///   ni services, ni sécurité, ni web. Chaque test s'exécute dans une transaction annulée à la fin.
/// - `replace = NONE` garde la base Oracle du conteneur au lieu d'une base embarquée.
/// - Les repositories écrits à la main (PL/SQL, SQL natif) ne sont pas des repositories Spring Data :
///   ils sont importés explicitement.
///
/// Toutes les classes de test partagent ainsi **exactement** la même configuration : Spring réutilise
/// un seul contexte, donc un seul conteneur Oracle, pour toute la couche de persistance.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ArchivageRepository.class, ReportingRepository.class})
public @interface TestPersistanceOracle {
}