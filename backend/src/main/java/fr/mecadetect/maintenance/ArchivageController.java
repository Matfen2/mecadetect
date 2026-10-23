package fr.mecadetect.maintenance;

import fr.mecadetect.api.ArchivageApi;
import fr.mecadetect.api.dto.ArchivageRequestDto;
import fr.mecadetect.api.dto.ArchivageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/// Tag Archivage du contrat, réservé à l'admin : archivage des interventions anciennes (UC-DB4).
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ArchivageController implements ArchivageApi {

    private final ArchivageService archivageService;

    /// Archive les interventions closes avant la date limite.
    ///
    /// @param requete date limite d'archivage
    /// @return le nombre d'interventions archivées
    @Override
    public ResponseEntity<ArchivageResponseDto> archiverInterventions(ArchivageRequestDto requete) {
        long archivees = archivageService.archiver(requete.getDateLimite());
        return ResponseEntity.ok(new ArchivageResponseDto().lignesArchivees(archivees));
    }
}
