package fr.mecadetect.equipement;

import fr.mecadetect.api.EquipementsApi;
import fr.mecadetect.api.dto.EquipementDto;
import fr.mecadetect.api.dto.EquipementRequestDto;
import fr.mecadetect.api.dto.PageEquipementDto;
import fr.mecadetect.api.dto.PageInterventionDto;
import fr.mecadetect.api.dto.StatutEquipementDto;
import fr.mecadetect.erreur.FonctionnaliteNonDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implémente l'interface EquipementsApi générée depuis le contrat.
 * Lecture : tout utilisateur connecté. Écriture : ADMIN uniquement.
 */
@RestController
public class EquipementController implements EquipementsApi {

    private final EquipementService service;

    public EquipementController(EquipementService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EquipementDto> creerEquipement(EquipementRequestDto equipementRequestDto) {
        var equipement = service.creer(EquipementMapper.versCommande(equipementRequestDto));
        return ResponseEntity.status(HttpStatus.CREATED).body(EquipementMapper.versDto(equipement));
    }

    @Override
    public ResponseEntity<EquipementDto> obtenirEquipement(Long idEquipement) {
        return ResponseEntity.ok(EquipementMapper.versDto(service.obtenir(idEquipement)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EquipementDto> modifierEquipement(Long idEquipement, EquipementRequestDto equipementRequestDto) {
        var equipement = service.modifier(idEquipement, EquipementMapper.versCommande(equipementRequestDto));
        return ResponseEntity.ok(EquipementMapper.versDto(equipement));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archiverEquipement(Long idEquipement) {
        service.archiver(idEquipement);
        return ResponseEntity.noContent().build();
    }

    // Tâche 2.3 (prochaine étape)
    @Override
    public ResponseEntity<PageEquipementDto> rechercherEquipements(
            Integer page, Integer size, String sort, StatutEquipementDto statut,
            Long idType, Long idZone, String recherche) {
        throw new FonctionnaliteNonDisponibleException("recherche des équipements");
    }

    // Sprint 3 : nécessite les interventions
    @Override
    public ResponseEntity<PageInterventionDto> historiqueInterventionsEquipement(
            Long idEquipement, Integer page, Integer size) {
        throw new FonctionnaliteNonDisponibleException("historique des interventions");
    }
}