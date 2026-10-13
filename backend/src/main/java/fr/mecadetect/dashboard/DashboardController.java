package fr.mecadetect.dashboard;

import fr.mecadetect.api.DashboardApi;
import fr.mecadetect.api.dto.ArchivageRequestDto;
import fr.mecadetect.api.dto.ArchivageResponseDto;
import fr.mecadetect.api.dto.DashboardDto;
import fr.mecadetect.erreur.FonctionnaliteNonDisponibleException;
import fr.mecadetect.maintenance.ArchivageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Tag Dashboard du contrat : tout est réservé à l'admin.
 * L'archivage est disponible (Sprint 4) ; tableau de bord et export CSV arrivent au Sprint 5 (501 d'ici là).
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController implements DashboardApi {

    private final ArchivageService archivageService;

    public DashboardController(ArchivageService archivageService) {
        this.archivageService = archivageService;
    }

    @Override
    public ResponseEntity<ArchivageResponseDto> archiverInterventions(ArchivageRequestDto requete) {
        long archivees = archivageService.archiver(requete.getDateLimite());
        return ResponseEntity.ok(new ArchivageResponseDto().lignesArchivees(archivees));
    }

    @Override
    public ResponseEntity<DashboardDto> obtenirDashboard() {
        throw new FonctionnaliteNonDisponibleException("tableau de bord (Sprint 5)");
    }

    @Override
    public ResponseEntity<Resource> exporterInterventions(LocalDate depuis, LocalDate jusqua) {
        throw new FonctionnaliteNonDisponibleException("export CSV des interventions (Sprint 5)");
    }
}