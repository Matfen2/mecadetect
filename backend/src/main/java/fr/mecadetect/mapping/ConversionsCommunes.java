package fr.mecadetect.mapping;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

/// Conversions de types utilisées par tous les mappers ; MapStruct les choisit d'après les types
/// source et cible.
@Component
public class ConversionsCommunes {

    /// Les dates sont stockées en UTC : elles sont exposées avec leur fuseau (format ISO 8601).
    ///
    /// @param date date stockée, en UTC
    /// @return la même date avec le décalage UTC, ou null
    public OffsetDateTime enUtc(LocalDateTime date) {
        return date == null ? null : date.atOffset(ZoneOffset.UTC);
    }

    /// Date reçue avec son fuseau, ramenée en UTC pour le stockage.
    ///
    /// @param date date reçue de l'API, avec son décalage
    /// @return la même date exprimée en UTC, sans décalage, ou null
    public LocalDateTime versUtc(OffsetDateTime date) {
        return date == null ? null : date.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /// Compteur exposé en entier 32 bits par le contrat.
    ///
    /// @param valeur compteur calculé en base
    /// @return la même valeur
    /// @throws ArithmeticException si la valeur dépasse un entier 32 bits, plutôt qu'un résultat faux
    public Integer versEntier(long valeur) {
        return Math.toIntExact(valeur);
    }

    /// Compteur facultatif exposé en entier 32 bits par le contrat.
    ///
    /// @param valeur compteur calculé en base, ou null
    /// @return la même valeur, ou null
    /// @throws ArithmeticException si la valeur dépasse un entier 32 bits
    public Integer versEntier(Long valeur) {
        return valeur == null ? null : Math.toIntExact(valeur);
    }

    /// Mois au format ISO 8601, par exemple 2026-10.
    ///
    /// @param mois le mois
    /// @return le mois au format `AAAA-MM`, ou null
    public String versTexte(YearMonth mois) {
        return mois == null ? null : mois.toString();
    }
}
