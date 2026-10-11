package fr.mecadetect.intervention;

import fr.mecadetect.api.dto.ClotureRequestDto;
import fr.mecadetect.api.dto.DeclarationPanneRequestDto;
import fr.mecadetect.api.dto.HistoriqueStatutDto;
import fr.mecadetect.api.dto.InterventionDetailDto;
import fr.mecadetect.api.dto.PlanificationRequestDto;
import fr.mecadetect.api.dto.PrioriteDto;
import fr.mecadetect.api.dto.RoleDto;
import fr.mecadetect.api.dto.StatutInterventionDto;
import fr.mecadetect.api.dto.UtilisateurResumeDto;
import fr.mecadetect.equipement.EquipementMapper;
import fr.mecadetect.utilisateur.Utilisateur;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Conversion entre les interventions et les DTO générés depuis le contrat. */
public final class InterventionMapper {

    private InterventionMapper() {
    }

    public static DeclarationCommande versCommande(DeclarationPanneRequestDto requete) {
        return new DeclarationCommande(
                requete.getIdEquipement(),
                requete.getTitre(),
                requete.getDescription(),
                Priorite.valueOf(requete.getPriorite().getValue()));
    }

    public static PlanificationCommande versCommande(PlanificationRequestDto requete) {
        return new PlanificationCommande(requete.getVersion(), requete.getIdTechnicien(), versUtc(requete.getDatePlanifiee()));
    }

    public static ClotureCommande versCommande(ClotureRequestDto requete) {
        return new ClotureCommande(requete.getVersion(), requete.getCompteRendu(), requete.getTempsPasseMin());
    }

    public static InterventionDetailDto versDetailDto(DetailIntervention detail) {
        var intervention = detail.intervention();
        return new InterventionDetailDto()
                .idIntervention(intervention.getId())
                .numero(intervention.getNumero())
                .titre(intervention.getTitre())
                .description(intervention.getDescription())
                .priorite(PrioriteDto.fromValue(intervention.getPriorite().name()))
                .statut(versStatutDto(intervention.getStatut()))
                .dateDemande(enUtc(intervention.getDateDemande()))
                .datePlanifiee(enUtc(intervention.getDatePlanifiee()))
                .dateDebut(enUtc(intervention.getDateDebut()))
                .dateFin(enUtc(intervention.getDateFin()))
                .tempsPasseMin(intervention.getTempsPasseMin())
                .compteRendu(intervention.getCompteRendu())
                .motifAnnulation(intervention.getMotifAnnulation())
                .version(intervention.getVersion())
                .equipement(EquipementMapper.versDto(intervention.getEquipement()))
                .demandeur(versResume(intervention.getDemandeur()))
                .technicien(intervention.getTechnicien() == null ? null : versResume(intervention.getTechnicien()))
                .historique(detail.historique().stream().map(InterventionMapper::versHistoriqueDto).toList());
    }

    static HistoriqueStatutDto versHistoriqueDto(HistoriqueStatut historique) {
        return new HistoriqueStatutDto()
                .ancienStatut(historique.getAncienStatut() == null ? null : versStatutDto(historique.getAncienStatut()))
                .nouveauStatut(versStatutDto(historique.getNouveauStatut()))
                .dateChangement(enUtc(historique.getDateChangement()))
                .auteur(versResume(historique.getAuteur()));
    }

    static UtilisateurResumeDto versResume(Utilisateur utilisateur) {
        return new UtilisateurResumeDto()
                .idUtilisateur(utilisateur.getId())
                .nom(utilisateur.getNom())
                .prenom(utilisateur.getPrenom())
                .role(RoleDto.fromValue(utilisateur.getRole().name()));
    }

    static StatutInterventionDto versStatutDto(StatutIntervention statut) {
        return StatutInterventionDto.fromValue(statut.name());
    }

    /** Date reçue avec son fuseau, ramenée en UTC pour le stockage. */
    static LocalDateTime versUtc(OffsetDateTime date) {
        return date == null ? null : date.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Les dates sont stockées en UTC : on les expose avec leur fuseau (format ISO 8601). */
    static OffsetDateTime enUtc(LocalDateTime date) {
        return date == null ? null : date.atOffset(ZoneOffset.UTC);
    }
}