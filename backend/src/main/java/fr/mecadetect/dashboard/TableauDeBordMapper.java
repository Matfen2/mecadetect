package fr.mecadetect.dashboard;

import fr.mecadetect.api.dto.DashboardChargeTechniciensInnerDto;
import fr.mecadetect.api.dto.DashboardDto;
import fr.mecadetect.api.dto.DashboardEvolutionMensuelleInnerDto;
import fr.mecadetect.api.dto.DashboardOuvertesParPrioriteInnerDto;
import fr.mecadetect.api.dto.DashboardTopEquipementsInnerDto;
import fr.mecadetect.api.dto.PrioriteDto;
import fr.mecadetect.intervention.Priorite;
import fr.mecadetect.mapping.ConfigurationMappers;
import fr.mecadetect.reporting.ChargeTechnicienPeriode;
import fr.mecadetect.reporting.EvolutionMensuelle;
import fr.mecadetect.reporting.TopEquipement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.SequencedMap;

/// Conversion du tableau de bord vers le DTO généré depuis le contrat.
///
/// Les compteurs, calculés en `long`, sont exposés en entiers 32 bits : la conversion par
/// [fr.mecadetect.mapping.ConversionsCommunes] échoue plutôt que de renvoyer une valeur tronquée.
@Mapper(config = ConfigurationMappers.class)
public interface TableauDeBordMapper {

    /// @param tableau indicateurs calculés par le service
    /// @return le tableau de bord exposé par l'API
    DashboardDto versDto(TableauDeBord tableau);

    /// @param top équipement du classement
    /// @return la ligne du classement exposée par l'API
    DashboardTopEquipementsInnerDto versDto(TopEquipement top);

    /// @param mois nombre de demandes d'un mois
    /// @return le mois exposé par l'API, au format `AAAA-MM`
    DashboardEvolutionMensuelleInnerDto versDto(EvolutionMensuelle mois);

    /// La charge porte sur le mois en cours : seules les interventions terminées et le temps passé
    /// sont exposés, pas les interventions ouvertes.
    ///
    /// @param charge charge d'un technicien
    /// @return la charge exposée par l'API
    @Mapping(target = "technicien.idUtilisateur", source = "idTechnicien")
    @Mapping(target = "technicien.prenom", source = "prenom")
    @Mapping(target = "technicien.nom", source = "nom")
    @Mapping(target = "technicien.role", constant = "TECHNICIEN")
    @Mapping(target = "interventionsMois", source = "terminees")
    @Mapping(target = "tempsPasseMinMois", source = "minutesPassees")
    DashboardChargeTechniciensInnerDto versDto(ChargeTechnicienPeriode charge);

    /// Interventions ouvertes par priorité. Écrite à la main : le service fournit une table
    /// ordonnée, le contrat attend une liste de paires.
    ///
    /// @param ouvertes nombre d'interventions ouvertes par priorité, dans l'ordre d'urgence
    /// @return une ligne par priorité, dans le même ordre
    default List<DashboardOuvertesParPrioriteInnerDto> versDto(SequencedMap<Priorite, Long> ouvertes) {
        return ouvertes.entrySet().stream()
                .map(entree -> new DashboardOuvertesParPrioriteInnerDto()
                        .priorite(PrioriteDto.fromValue(entree.getKey().name()))
                        .nombre(Math.toIntExact(entree.getValue())))
                .toList();
    }
}
