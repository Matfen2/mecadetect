package fr.mecadetect.technicien;

import fr.mecadetect.api.TechniciensApi;
import fr.mecadetect.api.dto.TechnicienDto;
import fr.mecadetect.intervention.InterventionMapper;
import fr.mecadetect.referentiel.ReferentielMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Techniciens avec habilitations et charge, pour choisir qui assigner (ADMIN). */
@RestController
public class TechnicienController implements TechniciensApi {

    private final TechnicienService service;

    public TechnicienController(TechnicienService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TechnicienDto>> listerTechniciens(Long idType) {
        return ResponseEntity.ok(service.lister(idType).stream()
                .map(TechnicienController::versDto)
                .toList());
    }

    private static TechnicienDto versDto(TechnicienVue vue) {
        var technicien = vue.technicien();
        return new TechnicienDto()
                .idUtilisateur(technicien.getId())
                .nom(technicien.getNom())
                .prenom(technicien.getPrenom())
                .habilitations(vue.habilitations().stream().map(ReferentielMapper::versDto).toList())
                .interventionsOuvertes(vue.interventionsOuvertes())
                .interventionEnCours(vue.interventionEnCours() == null
                        ? null : InterventionMapper.versResumeDto(vue.interventionEnCours()));
    }
}