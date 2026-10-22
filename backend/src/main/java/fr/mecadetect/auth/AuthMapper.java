package fr.mecadetect.auth;

import fr.mecadetect.api.dto.LoginResponseDto;
import fr.mecadetect.mapping.ConfigurationMappers;
import fr.mecadetect.utilisateur.UtilisateurMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// Conversion du résultat d'une connexion vers le DTO généré depuis le contrat.
@Mapper(config = ConfigurationMappers.class, uses = UtilisateurMapper.class)
public interface AuthMapper {

    /// @param connexion jeton généré et utilisateur connecté
    /// @return le jeton, sa durée de validité en secondes et le résumé de l'utilisateur
    @Mapping(target = "token", source = "jeton.valeur")
    @Mapping(target = "expireDans", source = "jeton.expireDansSecondes")
    LoginResponseDto versDto(AuthService.ResultatConnexion connexion);
}
