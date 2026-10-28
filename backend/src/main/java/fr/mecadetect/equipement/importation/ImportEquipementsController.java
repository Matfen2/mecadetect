package fr.mecadetect.equipement.importation;

import fr.mecadetect.api.ImportsApi;
import fr.mecadetect.api.dto.RapportImportDto;
import fr.mecadetect.erreur.DonneesInvalidesException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/// Tag Imports du contrat, réservé à l'admin : import du parc d'équipements par lots.
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ImportEquipementsController implements ImportsApi {

    private final ImportEquipementsService service;
    private final ImportEquipementsMapper mapper;

    /// Importe les équipements du fichier CSV.
    ///
    /// @param fichier fichier CSV du parc
    /// @return le rapport de l'import
    @Override
    public ResponseEntity<RapportImportDto> importerEquipements(MultipartFile fichier) {
        if (fichier.isEmpty()) {
            throw new DonneesInvalidesException("FICHIER_VIDE", "Le fichier envoyé est vide.");
        }
        try (var contenu = fichier.getInputStream()) {
            return ResponseEntity.ok(mapper.versDto(service.importer(contenu)));
        } catch (IOException e) {
            throw new UncheckedIOException("Le fichier envoyé n'a pas pu être lu.", e);
        }
    }
}
