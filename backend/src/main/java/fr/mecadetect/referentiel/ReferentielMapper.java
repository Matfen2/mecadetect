package fr.mecadetect.referentiel;

import fr.mecadetect.api.dto.TypeEquipementDto;
import fr.mecadetect.api.dto.ZoneDto;
import fr.mecadetect.mapping.ConfigurationMappers;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/// Conversion des entités du référentiel vers les DTO générés depuis le contrat.
/// L'implémentation est générée par MapStruct à la compilation.
@Mapper(config = ConfigurationMappers.class)
public interface ReferentielMapper {

    /// @param zone zone du référentiel
    /// @return la zone exposée par l'API
    @Mapping(target = "idZone", source = "id")
    ZoneDto versDto(Zone zone);

    /// @param type type d'équipement du référentiel
    /// @return le type exposé par l'API
    @Mapping(target = "idType", source = "id")
    TypeEquipementDto versDto(TypeEquipement type);

    /// @param zones zones du référentiel
    /// @return les zones exposées par l'API, dans le même ordre
    List<ZoneDto> versZonesDto(List<Zone> zones);

    /// @param types types d'équipement du référentiel
    /// @return les types exposés par l'API, dans le même ordre
    List<TypeEquipementDto> versTypesDto(List<TypeEquipement> types);
}
