package fr.mecadetect.dashboard;

import fr.mecadetect.api.DashboardApi;
import fr.mecadetect.api.dto.ArchivageRequestDto;
import fr.mecadetect.api.dto.ArchivageResponseDto;
import fr.mecadetect.api.dto.DashboardChargeTechniciensInnerDto;
import fr.mecadetect.api.dto.DashboardDto;
import fr.mecadetect.api.dto.DashboardEvolutionMensuelleInnerDto;
import fr.mecadetect.api.dto.DashboardOuvertesParPrioriteInnerDto;
import fr.mecadetect.api.dto.DashboardTopEquipementsInnerDto;
import fr.mecadetect.api.dto.PrioriteDto;
import fr.mecadetect.api.dto.RoleDto;
import fr.mecadetect.api.dto.UtilisateurResumeDto;
import fr.mecadetect.erreur.FonctionnaliteNonDisponibleException;
import fr.mecadetect.maintenance.ArchivageService;
import fr.mecadetect.reporting.ChargeTechnicienPeriode;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Tag Dashboard du contrat : tout est réservé à l'admin.
 * Tableau de bord (tâche 5.1) et archivage (Sprint 4) disponibles ; l'export CSV arrive à la tâche 5.3.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;
    private final ArchivageService archivageService;

    public DashboardController(DashboardService dashboardService, ArchivageService archivageService) {
        this.dashboardService = dashboardService;
        this.archivageService = archivageService;
    }

    @Override
    public ResponseEntity<DashboardDto> obtenirDashboard() {
        return ResponseEntity.ok(versDto(dashboardService.calculer()));
    }

    @Override
    public ResponseEntity<ArchivageResponseDto> archiverInterventions(ArchivageRequestDto requete) {
        long archivees = archivageService.archiver(requete.getDateLimite());
        return ResponseEntity.ok(new ArchivageResponseDto().lignesArchivees(archivees));
    }

    @Override
    public ResponseEntity<Resource> exporterInterventions(LocalDate depuis, LocalDate jusqua) {
        throw new FonctionnaliteNonDisponibleException("export CSV des interventions (tâche 5.3)");
    }

    private static DashboardDto versDto(TableauDeBord tableau) {
        return new DashboardDto()
                .ouvertesParPriorite(tableau.ouvertesParPriorite().entrySet().stream()
                        .map(e -> new DashboardOuvertesParPrioriteInnerDto()
                                .priorite(PrioriteDto.fromValue(e.getKey().name()))
                                .nombre(Math.toIntExact(e.getValue())))
                        .toList())
                .equipementsEnPanne(Math.toIntExact(tableau.equipementsEnPanne()))
                .tempsMoyenResolutionHeures(tableau.tempsMoyenResolutionHeures())
                .topEquipements(tableau.topEquipements().stream()
                        .map(top -> new DashboardTopEquipementsInnerDto()
                                .rang(top.rang())
                                .reference(top.reference())
                                .nom(top.nom())
                                .nombrePannes(Math.toIntExact(top.nombrePannes())))
                        .toList())
                .evolutionMensuelle(tableau.evolutionMensuelle().stream()
                        .map(mois -> new DashboardEvolutionMensuelleInnerDto()
                                .mois(mois.mois().toString())
                                .nombre(Math.toIntExact(mois.nombre()))
                                .variation(mois.variation() == null ? null : Math.toIntExact(mois.variation())))
                        .toList())
                .chargeTechniciens(tableau.chargeTechniciens().stream()
                        .map(DashboardController::versDto)
                        .toList());
    }

    private static DashboardChargeTechniciensInnerDto versDto(ChargeTechnicienPeriode charge) {
        return new DashboardChargeTechniciensInnerDto()
                .technicien(new UtilisateurResumeDto()
                        .idUtilisateur(charge.idTechnicien())
                        .prenom(charge.prenom())
                        .nom(charge.nom())
                        .role(RoleDto.TECHNICIEN))
                .interventionsMois(Math.toIntExact(charge.terminees()))
                .tempsPasseMinMois(Math.toIntExact(charge.minutesPassees()));
    }
}