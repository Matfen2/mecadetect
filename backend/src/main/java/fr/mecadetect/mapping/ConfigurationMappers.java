package fr.mecadetect.mapping;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/// Configuration commune à tous les mappers MapStruct du projet.
///
/// - **Beans Spring injectés par constructeur** : un mapper en utilise d'autres sans état partagé.
/// - **Un champ cible non renseigné fait échouer la compilation** : si le contrat OpenAPI ajoute
///   une propriété à un DTO, le build signale tous les mappers à compléter.
/// - Les conversions transverses (dates UTC, entiers) sont dans [ConversionsCommunes].
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = ConversionsCommunes.class)
public interface ConfigurationMappers {
}
