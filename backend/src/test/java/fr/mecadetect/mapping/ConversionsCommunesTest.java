package fr.mecadetect.mapping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Conversions partagées par les mappers MapStruct.
class ConversionsCommunesTest {

    private final ConversionsCommunes conversions = new ConversionsCommunes();

    @Test
    @DisplayName("Une date reçue à l'heure de Paris est stockée en UTC, puis exposée en UTC")
    void allerRetourUtc() {
        var aParis = OffsetDateTime.of(2026, 10, 21, 14, 30, 0, 0, ZoneOffset.ofHours(2));

        LocalDateTime stockee = conversions.versUtc(aParis);

        assertThat(stockee).isEqualTo(LocalDateTime.of(2026, 10, 21, 12, 30));
        assertThat(conversions.enUtc(stockee)).isEqualTo(aParis.withOffsetSameInstant(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Une date absente reste absente dans les deux sens")
    void dateNulle() {
        assertThat(conversions.versUtc(null)).isNull();
        assertThat(conversions.enUtc(null)).isNull();
    }

    @Test
    @DisplayName("Un compteur trop grand pour un entier 32 bits est refusé plutôt que tronqué")
    void compteurTropGrand() {
        assertThat(conversions.versEntier(42L)).isEqualTo(42);
        assertThat(conversions.versEntier((Long) null)).isNull();
        assertThatThrownBy(() -> conversions.versEntier(Integer.MAX_VALUE + 1L))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("Un mois est exposé au format ISO 8601 AAAA-MM")
    void mois() {
        assertThat(conversions.versTexte(YearMonth.of(2026, 3))).isEqualTo("2026-03");
    }
}
