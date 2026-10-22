package fr.mecadetect.dashboard;

import fr.mecadetect.api.DashboardApi;
import fr.mecadetect.api.dto.DashboardDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/// Tag Dashboard du contrat, réservé à l'admin : indicateurs du tableau de bord (tâche 5.1).
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;
    private final TableauDeBordMapper mapper;

    public DashboardController(DashboardService dashboardService, TableauDeBordMapper mapper) {
        this.dashboardService = dashboardService;
        this.mapper = mapper;
    }

    /// @return les indicateurs du tableau de bord, calculés à la date courante
    @Override
    public ResponseEntity<DashboardDto> obtenirDashboard() {
        return ResponseEntity.ok(mapper.versDto(dashboardService.calculer()));
    }
}
