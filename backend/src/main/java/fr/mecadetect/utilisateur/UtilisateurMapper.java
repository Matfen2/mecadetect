package fr.mecadetect.utilisateur;

import fr.mecadetect.api.dto.UtilisateurResumeDto;
import fr.mecadetect.mapping.ConfigurationMappers;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// Conversion des utilisateurs vers les DTO générés depuis le contrat.
/// Seul le résumé est exposé : ni l'email ni l'empreinte du mot de passe ne quittent le back-end.
@Mapper(config = ConfigurationMappers.class)
public interface UtilisateurMapper {

    /// @param utilisateur utilisateur
    /// @return son identifiant, son nom, son prénom et son rôle
    @Mapping(target = "idUtilisateur", source = "id")
    UtilisateurResumeDto versResumeDto(Utilisateur utilisateur);
}
