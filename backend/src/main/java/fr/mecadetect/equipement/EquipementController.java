package fr.mecadetect.equipement;

import fr.mecadetect.api.EquipementsApi;
import fr.mecadetect.api.dto.EquipementDto;
import fr.mecadetect.api.dto.EquipementRequestDto;
import fr.mecadetect.api.dto.PageEquipementDto;
import fr.mecadetect.api.dto.PageInterventionDto;
import fr.mecadetect.api.dto.StatutEquipementDto;
import fr.mecadetect.intervention.InterventionMapper;
import fr.mecadetect.intervention.InterventionService;
import fr.mecadetect.intervention.PaginationIntervention;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/// Implémente l'interface EquipementsApi générée depuis le contrat.
/// Lecture : tout utilisateur connecté. Écriture : ADMIN uniquement.
@RestController
public class EquipementController implements EquipementsApi {

    private final EquipementService service;
    private final InterventionService interventionService;
    private final EquipementMapper mapper;
    private final InterventionMapper interventionMapper;

    public EquipementController(EquipementService service, InterventionService interventionService,
                                EquipementMapper mapper, InterventionMapper interventionMapper) {
        this.service = service;
        this.interventionService = interventionService;
        this.mapper = mapper;
        this.interventionMapper = interventionMapper;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EquipementDto> creerEquipement(EquipementRequestDto equipementRequestDto) {
        var equipement = service.creer(mapper.versCommande(equipementRequestDto));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.versDto(equipement));
    }

    @Override
    public ResponseEntity<EquipementDto> obtenirEquipement(Long idEquipement) {
        return ResponseEntity.ok(mapper.versDto(service.obtenir(idEquipement)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EquipementDto> modifierEquipement(Long idEquipement, EquipementRequestDto equipementRequestDto) {
        var equipement = service.modifier(idEquipement, mapper.versCommande(equipementRequestDto));
        return ResponseEntity.ok(mapper.versDto(equipement));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archiverEquipement(Long idEquipement) {
        service.archiver(idEquipement);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PageEquipementDto> rechercherEquipements(
            Integer page, Integer size, String sort, StatutEquipementDto statut,
            Long idType, Long idZone, String recherche) {
        var criteres = new CriteresRechercheEquipement(
                statut != null ? StatutEquipement.valueOf(statut.getValue()) : null,
                idType, idZone, recherche);
        var resultat = service.rechercher(criteres, PaginationEquipement.versPageable(page, size, sort));
        return ResponseEntity.ok(mapper.versPageDto(resultat));
    }

    @Override
    public ResponseEntity<PageInterventionDto> historiqueInterventionsEquipement(
            Long idEquipement, Integer page, Integer size) {
        var resultat = interventionService.historiqueEquipement(
                idEquipement, PaginationIntervention.versPageable(page, size, null));
        return ResponseEntity.ok(interventionMapper.versPageDto(resultat));
    }
}