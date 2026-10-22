package fr.mecadetect;

import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Règles transverses du schéma, vérifiées sur la base migrée par Flyway.
@TestPersistanceOracle
class SchemaOracleTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ZoneRepository zoneRepository;

    @Test
    @DisplayName("Aucune colonne VARCHAR2 des tables métier n'a sa longueur exprimée en octets")
    void longueursEnCaracteres() {
        // La table de Flyway n'appartient pas au schéma métier : elle est exclue
        List<String> colonnesEnOctets = jdbc.queryForList("""
                SELECT c.table_name || '.' || c.column_name
                  FROM user_tab_columns c
                  JOIN user_tables t ON t.table_name = c.table_name
                 WHERE c.data_type = 'VARCHAR2'
                   AND c.char_used = 'B'
                   AND LOWER(c.table_name) <> 'flyway_schema_history'
                 ORDER BY 1
                """, String.class);

        assertThat(colonnesEnOctets).isEmpty();
    }

    @Test
    @DisplayName("Un libellé de 100 caractères accentués est accepté (200 octets en UTF-8)")
    void libelleAccentue() {
        String libelle = "é".repeat(100);

        Zone zone = zoneRepository.saveAndFlush(new Zone("ÉTÉ", libelle));

        assertThat(jdbc.queryForObject("SELECT libelle FROM zone WHERE id_zone = ?", String.class, zone.getId()))
                .isEqualTo(libelle);
    }

    @Test
    @DisplayName("PKG_MAINTENANCE et le trigger d'historique restent valides après la migration")
    void objetsPlsqlValides() {
        List<String> invalides = jdbc.queryForList(
                "SELECT object_type || ' ' || object_name FROM user_objects WHERE status = 'INVALID' ORDER BY 1",
                String.class);

        assertThat(invalides).isEmpty();
    }
}