package fr.mecadetect.technicien;

import fr.mecadetect.api.TechniciensApi;
import fr.mecadetect.api.dto.TechnicienDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Techniciens avec habilitations et charge, pour choisir qui assigner (ADMIN).
@RestController
public class TechnicienController implements TechniciensApi {

    private final TechnicienService service;
    private final TechnicienMapper mapper;

    public TechnicienController(TechnicienService service, TechnicienMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TechnicienDto>> listerTechniciens(Long idType) {
        return ResponseEntity.ok(mapper.versDtos(service.lister(idType)));
    }
}
