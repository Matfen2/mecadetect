package fr.mecadetect.referentiel;

import fr.mecadetect.api.dto.TypeEquipementDto;
import fr.mecadetect.api.dto.ZoneDto;

/** Conversion des entités du référentiel vers les DTO générés depuis le contrat. */
public final class ReferentielMapper {

    private ReferentielMapper() {
    }

    public static ZoneDto versDto(Zone zone) {
        return new ZoneDto()
                .idZone(zone.getId())
                .code(zone.getCode())
                .libelle(zone.getLibelle());
    }

    public static TypeEquipementDto versDto(TypeEquipement type) {
        return new TypeEquipementDto()
                .idType(type.getId())
                .libelle(type.getLibelle());
    }
}