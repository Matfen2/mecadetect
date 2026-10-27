package fr.mecadetect.equipement.importation;

import fr.mecadetect.api.dto.RapportImportDto;
import fr.mecadetect.mapping.ConfigurationMappers;
import org.mapstruct.Mapper;

/// Conversion du rapport d'import vers le DTO généré depuis le contrat.
/// L'implémentation est générée par MapStruct à la compilation.
@Mapper(config = ConfigurationMappers.class)
public interface ImportEquipementsMapper {

    /// @param resultat rapport de l'import
    /// @return le rapport exposé par l'API
    RapportImportDto versDto(ResultatImport resultat);
}
