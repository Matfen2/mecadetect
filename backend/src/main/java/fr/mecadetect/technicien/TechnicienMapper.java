package fr.mecadetect.technicien;

import fr.mecadetect.api.dto.TechnicienDto;
import fr.mecadetect.intervention.InterventionMapper;
import fr.mecadetect.mapping.ConfigurationMappers;
import fr.mecadetect.referentiel.ReferentielMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/// Conversion des techniciens, avec leurs habilitations et leur charge, vers les DTO du contrat.
@Mapper(config = ConfigurationMappers.class, uses = {ReferentielMapper.class, InterventionMapper.class})
public interface TechnicienMapper {

    /// @param vue technicien, habilitations et charge
    /// @return le technicien exposé par l'API
    @Mapping(target = "idUtilisateur", source = "technicien.id")
    @Mapping(target = "nom", source = "technicien.nom")
    @Mapping(target = "prenom", source = "technicien.prenom")
    TechnicienDto versDto(TechnicienVue vue);

    /// @param vues techniciens, habilitations et charge
    /// @return les techniciens exposés par l'API, dans le même ordre
    List<TechnicienDto> versDtos(List<TechnicienVue> vues);
}
