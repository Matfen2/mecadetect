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
import fr.mecadetect.erreur.FonctionnaliteNonDisponibleException;
import fr.mecadetect.securite.UtilisateurCourant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Implémente l'interface InterventionsApi générée depuis le contrat.
 * Les endpoints arrivent au fil du Sprint 3 ; ceux qui ne sont pas encore prêts répondent 501.
 */
@RestController
public class InterventionController implements InterventionsApi {

    private final InterventionService service;
    private final UtilisateurCourant utilisateurCourant;

    public InterventionController(InterventionService service, UtilisateurCourant utilisateurCourant) {
        this.service = service;
        this.utilisateurCourant = utilisateurCourant;
    }

    @Override
    @PreAuthorize("hasAnyRole('DEMANDEUR', 'ADMIN')")
    public ResponseEntity<InterventionDetailDto> declarerPanne(DeclarationPanneRequestDto declarationPanneRequestDto) {
        var detail = service.declarer(InterventionMapper.versCommande(declarationPanneRequestDto), utilisateurCourant.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(InterventionMapper.versDetailDto(detail));
    }

    @Override
    public ResponseEntity<InterventionDetailDto> obtenirIntervention(Long idIntervention) {
        throw new FonctionnaliteNonDisponibleException("détail d'une intervention");
    }

    @Override
    public ResponseEntity<PageInterventionDto> rechercherInterventions(
            Integer page, Integer size, String sort, StatutInterventionDto statut, PrioriteDto priorite,
            Long idTechnicien, LocalDate depuis, LocalDate jusqua) {
        throw new FonctionnaliteNonDisponibleException("recherche des interventions");
    }

    @Override
    public ResponseEntity<PageInterventionDto> mesInterventions(Integer page, Integer size) {
        throw new FonctionnaliteNonDisponibleException("mes interventions");
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InterventionDetailDto> planifierIntervention(
            Long idIntervention, PlanificationRequestDto planificationRequestDto) {
        var detail = service.planifier(idIntervention, InterventionMapper.versCommande(planificationRequestDto),
                utilisateurCourant.id());
        return ResponseEntity.ok(InterventionMapper.versDetailDto(detail));
    }

    @Override
    public ResponseEntity<InterventionDetailDto> demarrerIntervention(
            Long idIntervention, VersionRequestDto versionRequestDto) {
        throw new FonctionnaliteNonDisponibleException("démarrage");
    }

    @Override
    public ResponseEntity<InterventionDetailDto> cloturerIntervention(
            Long idIntervention, ClotureRequestDto clotureRequestDto) {
        throw new FonctionnaliteNonDisponibleException("clôture");
    }

    @Override
    public ResponseEntity<InterventionDetailDto> annulerIntervention(
            Long idIntervention, AnnulationRequestDto annulationRequestDto) {
        throw new FonctionnaliteNonDisponibleException("annulation");
    }
}