package fr.mecadetect.intervention;

import fr.mecadetect.api.InterventionsApi;
import fr.mecadetect.api.dto.AnnulationRequestDto;
import fr.mecadetect.api.dto.ClotureRequestDto;
import fr.mecadetect.api.dto.DeclarationPanneRequestDto;
import fr.mecadetect.api.dto.InterventionDetailDto;
import fr.mecadetect.api.dto.PageInterventionDto;
import fr.mecadetect.api.dto.PlanificationRequestDto;
import fr.mecadetect.api.dto.PrioriteDto;
import fr.mecadetect.api.dto.StatutInterventionDto;
import fr.mecadetect.api.dto.VersionRequestDto;
import fr.mecadetect.erreur.ActionNonAutoriseeException;
import fr.mecadetect.securite.UtilisateurCourant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/// Implémente l'interface InterventionsApi générée depuis le contrat.
/// Consultation : tout utilisateur connecté. Actions : selon le rôle (RG-01, RG-02).
@RestController
public class InterventionController implements InterventionsApi {

    private final InterventionService service;
    private final UtilisateurCourant utilisateurCourant;
    private final InterventionMapper mapper;

    public InterventionController(InterventionService service, UtilisateurCourant utilisateurCourant,
                                  InterventionMapper mapper) {
        this.service = service;
        this.utilisateurCourant = utilisateurCourant;
        this.mapper = mapper;
    }

    @Override
    @PreAuthorize("hasAnyRole('DEMANDEUR', 'ADMIN')")
    public ResponseEntity<InterventionDetailDto> declarerPanne(DeclarationPanneRequestDto declarationPanneRequestDto) {
        var detail = service.declarer(mapper.versCommande(declarationPanneRequestDto), utilisateurCourant.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.versDetailDto(detail));
    }

    @Override
    public ResponseEntity<InterventionDetailDto> obtenirIntervention(Long idIntervention) {
        return ResponseEntity.ok(mapper.versDetailDto(service.obtenir(idIntervention)));
    }

    @Override
    public ResponseEntity<PageInterventionDto> rechercherInterventions(
            Integer page, Integer size, String sort, StatutInterventionDto statut, PrioriteDto priorite,
            Long idTechnicien, LocalDate depuis, LocalDate jusqua) {
        var criteres = new CriteresRechercheIntervention(
                statut != null ? StatutIntervention.valueOf(statut.getValue()) : null,
                priorite != null ? Priorite.valueOf(priorite.getValue()) : null,
                idTechnicien, depuis, jusqua);
        var resultat = service.rechercher(criteres, PaginationIntervention.versPageable(page, size, sort));
        return ResponseEntity.ok(mapper.versPageDto(resultat));
    }

    /// Interventions planifiées et en cours d'un technicien.
    ///
    /// Un technicien ne consulte que les siennes : l'identifiant du chemin est comparé à celui
    /// du jeton. L'admin peut consulter celles de n'importe quel technicien.
    ///
    /// @param idTechnicien identifiant du technicien
    /// @param page         numéro de page
    /// @param size         nombre d'éléments par page
    /// @return la page d'interventions, celle en cours d'abord
    @Override
    @PreAuthorize("hasAnyRole('TECHNICIEN', 'ADMIN')")
    public ResponseEntity<PageInterventionDto> listerInterventionsTechnicien(Long idTechnicien, Integer page, Integer size) {
        if (!utilisateurCourant.estAdmin() && !idTechnicien.equals(utilisateurCourant.id())) {
            throw new ActionNonAutoriseeException("INTERVENTIONS_D_UN_AUTRE_TECHNICIEN",
                    "Un technicien ne peut consulter que ses propres interventions.");
        }
        var resultat = service.aTraiterPar(idTechnicien, PaginationIntervention.sansTri(page, size));
        return ResponseEntity.ok(mapper.versPageDto(resultat));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InterventionDetailDto> planifierIntervention(
            Long idIntervention, PlanificationRequestDto planificationRequestDto) {
        var detail = service.planifier(idIntervention, mapper.versCommande(planificationRequestDto),
                utilisateurCourant.id());
        return ResponseEntity.ok(mapper.versDetailDto(detail));
    }

    @Override
    @PreAuthorize("hasRole('TECHNICIEN')")
    public ResponseEntity<InterventionDetailDto> demarrerIntervention(
            Long idIntervention, VersionRequestDto versionRequestDto) {
        var detail = service.demarrer(idIntervention, versionRequestDto.getVersion(), utilisateurCourant.id());
        return ResponseEntity.ok(mapper.versDetailDto(detail));
    }

    @Override
    @PreAuthorize("hasRole('TECHNICIEN')")
    public ResponseEntity<InterventionDetailDto> cloturerIntervention(
            Long idIntervention, ClotureRequestDto clotureRequestDto) {
        var detail = service.cloturer(idIntervention, mapper.versCommande(clotureRequestDto),
                utilisateurCourant.id());
        return ResponseEntity.ok(mapper.versDetailDto(detail));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InterventionDetailDto> annulerIntervention(
            Long idIntervention, AnnulationRequestDto annulationRequestDto) {
        var detail = service.annuler(idIntervention, mapper.versCommande(annulationRequestDto),
                utilisateurCourant.id());
        return ResponseEntity.ok(mapper.versDetailDto(detail));
    }
}