package fr.mecadetect.export;

import fr.mecadetect.api.ExportsApi;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/// Tag Exports du contrat, réservé à l'admin : export CSV des interventions (tâche 5.3).
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class ExportController implements ExportsApi {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ExportInterventionsService exportService;

    public ExportController(ExportInterventionsService exportService) {
        this.exportService = exportService;
    }

    /// Envoie le fichier CSV en pièce jointe, produit pendant le téléchargement.
    ///
    /// @param depuis premier jour de la période, inclus
    /// @param jusqua dernier jour de la période, inclus
    /// @return le fichier CSV
    @Override
    public ResponseEntity<Resource> exporterInterventions(LocalDate depuis, LocalDate jusqua) {
        ExportCsv export = exportService.exporter(depuis, jusqua);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.nomFichier()).build().toString())
                .body(export.contenu());
    }
}
