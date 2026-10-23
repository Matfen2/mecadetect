package fr.mecadetect.equipement;

import fr.mecadetect.api.dto.EquipementDto;
import fr.mecadetect.api.dto.EquipementRequestDto;
import fr.mecadetect.api.dto.PageEquipementDto;
import fr.mecadetect.mapping.ConfigurationMappers;
import fr.mecadetect.referentiel.ReferentielMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

import java.util.List;

/// Conversion entre les équipements et les DTO générés depuis le contrat.
/// L'implémentation est générée par MapStruct à la compilation.
@Mapper(config = ConfigurationMappers.class, uses = ReferentielMapper.class)
public interface EquipementMapper {

    /// @param equipement équipement chargé avec son type et sa zone
    /// @return l'équipement exposé par l'API
    @Mapping(target = "idEquipement", source = "id")
    EquipementDto versDto(Equipement equipement);

    /// @param equipements équipements chargés avec leur type et leur zone
    /// @return les équipements exposés par l'API, dans le même ordre
    List<EquipementDto> versDtos(List<Equipement> equipements);

    /// @param requete corps de la requête de création ou de modification
    /// @return la commande transmise au service
    EquipementCommande versCommande(EquipementRequestDto requete);

    /// Page d'équipements. Écrite à la main : une [Page] Spring est itérable,
    /// et MapStruct ne convertit pas un itérable en objet simple.
    ///
    /// @param page page renvoyée par le service
    /// @return la page exposée par l'API
    default PageEquipementDto versPageDto(Page<Equipement> page) {
        return new PageEquipementDto()
                .page(page.getNumber())
                .taille(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .contenu(versDtos(page.getContent()));
    }
}
