package fr.mecadetect.referentiel;

import fr.mecadetect.api.ReferentielApi;
import fr.mecadetect.api.dto.TypeEquipementDto;
import fr.mecadetect.api.dto.ZoneDto;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Listes de référence utilisées par les formulaires (zones et types d'équipement). */
@RestController
@Transactional(readOnly = true)
public class ReferentielController implements ReferentielApi {

    private final ZoneRepository zoneRepository;
    private final TypeEquipementRepository typeRepository;

    public ReferentielController(ZoneRepository zoneRepository, TypeEquipementRepository typeRepository) {
        this.zoneRepository = zoneRepository;
        this.typeRepository = typeRepository;
    }

    @Override
    public ResponseEntity<List<ZoneDto>> listerZones() {
        return ResponseEntity.ok(zoneRepository.findAllByOrderByCodeAsc().stream()
                .map(ReferentielMapper::versDto)
                .toList());
    }

    @Override
    public ResponseEntity<List<TypeEquipementDto>> listerTypesEquipement() {
        return ResponseEntity.ok(typeRepository.findAllByOrderByLibelleAsc().stream()
                .map(ReferentielMapper::versDto)
                .toList());
    }
}