package fr.mecadetect.intervention.api;

import fr.mecadetect.api.dto.AnnulationRequestDto;
import fr.mecadetect.api.dto.ClotureRequestDto;
import fr.mecadetect.api.dto.DeclarationPanneRequestDto;
import fr.mecadetect.api.dto.HistoriqueStatutDto;
import fr.mecadetect.api.dto.InterventionDetailDto;
import fr.mecadetect.api.dto.InterventionResumeDto;
import fr.mecadetect.api.dto.PageInterventionDto;
import fr.mecadetect.api.dto.PlanificationRequestDto;
import fr.mecadetect.equipement.EquipementMapper;
import fr.mecadetect.intervention.application.AnnulationCommande;
import fr.mecadetect.intervention.application.ClotureCommande;
import fr.mecadetect.intervention.application.DeclarationCommande;
import fr.mecadetect.intervention.application.DetailIntervention;
import fr.mecadetect.intervention.application.PlanificationCommande;
import fr.mecadetect.intervention.domain.HistoriqueStatut;
import fr.mecadetect.intervention.domain.InterventionResume;
import fr.mecadetect.mapping.ConfigurationMappers;
import fr.mecadetect.utilisateur.UtilisateurMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

import java.util.List;

/// Conversion entre les interventions et les DTO générés depuis le contrat.
///
/// L'implémentation est générée par MapStruct à la compilation. Les dates passent par
/// [fr.mecadetect.mapping.ConversionsCommunes] : stockées en UTC, exposées avec leur fuseau.
@Mapper(config = ConfigurationMappers.class, uses = {EquipementMapper.class, UtilisateurMapper.class})
public interface InterventionMapper {

    // ------------------------------------------------------------------ requêtes → commandes

    /// @param requete corps de la déclaration de panne
    /// @return la commande transmise au service
    DeclarationCommande versCommande(DeclarationPanneRequestDto requete);

    /// @param requete corps de la planification ; la date est ramenée en UTC
    /// @return la commande transmise au service
    PlanificationCommande versCommande(PlanificationRequestDto requete);

    /// @param requete corps de la clôture
    /// @return la commande transmise au service
    ClotureCommande versCommande(ClotureRequestDto requete);

    /// @param requete corps de l'annulation
    /// @return la commande transmise au service
    AnnulationCommande versCommande(AnnulationRequestDto requete);

    // ------------------------------------------------------------------ résultats → DTO

    /// Détail d'une intervention : ses propriétés, son équipement, ses acteurs et son historique.
    ///
    /// @param detail intervention et historique entièrement chargés
    /// @return le détail exposé par l'API
    @Mapping(target = ".", source = "intervention")
    @Mapping(target = "idIntervention", source = "intervention.id")
    InterventionDetailDto versDetailDto(DetailIntervention detail);

    /// @param historique changement de statut, avec son auteur
    /// @return le changement exposé par l'API
    HistoriqueStatutDto versHistoriqueDto(HistoriqueStatut historique);

    /// @param resume ligne de liste projetée par la requête
    /// @return la ligne exposée par l'API
    @Mapping(target = "technicienNomComplet", expression = "java(resume.technicienNomComplet())")
    InterventionResumeDto versResumeDto(InterventionResume resume);

    /// @param resumes lignes de liste projetées par la requête
    /// @return les lignes exposées par l'API, dans le même ordre
    List<InterventionResumeDto> versResumesDto(List<InterventionResume> resumes);

    /// Page d'interventions. Écrite à la main : une [Page] Spring est itérable,
    /// et MapStruct ne convertit pas un itérable en objet simple.
    ///
    /// @param page page renvoyée par le service
    /// @return la page exposée par l'API
    default PageInterventionDto versPageDto(Page<InterventionResume> page) {
        return new PageInterventionDto()
                .page(page.getNumber())
                .taille(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .contenu(versResumesDto(page.getContent()));
    }
}
