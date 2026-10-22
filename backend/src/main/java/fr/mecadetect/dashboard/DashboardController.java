package fr.mecadetect.dashboard;

import fr.mecadetect.api.DashboardApi;
import fr.mecadetect.api.dto.ArchivageRequestDto;
import fr.mecadetect.api.dto.ArchivageResponseDto;
import fr.mecadetect.api.dto.DashboardDto;
import fr.mecadetect.export.ExportCsv;
import fr.mecadetect.export.ExportInterventionsService;
import fr.mecadetect.maintenance.ArchivageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/// Tag Dashboard du contrat, entièrement réservé à l'admin : tableau de bord (tâche 5.1),
/// export CSV (tâche 5.3) et archivage (Sprint 4).
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;
    private final ArchivageService archivageService;
    private final ExportInterventionsService exportService;
    private final TableauDeBordMapper mapper;

    public DashboardController(DashboardService dashboardService, ArchivageService archivageService,
                               ExportInterventionsService exportService, TableauDeBordMapper mapper) {
        this.dashboardService = dashboardService;
        this.archivageService = archivageService;
        this.exportService = exportService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<DashboardDto> obtenirDashboard() {
        return ResponseEntity.ok(mapper.versDto(dashboardService.calculer()));
    }

    @Override
    public ResponseEntity<ArchivageResponseDto> archiverInterventions(ArchivageRequestDto requete) {
        long archivees = archivageService.archiver(requete.getDateLimite());
        return ResponseEntity.ok(new ArchivageResponseDto().lignesArchivees(archivees));
    }

    @Override
    public ResponseEntity<Resource> exporterInterventions(LocalDate depuis, LocalDate jusqua) {
        ExportCsv export = exportService.exporter(depuis, jusqua);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.nomFichier()).build().toString())
                .body(export.contenu());
    }
}
