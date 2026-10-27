package fr.mecadetect.equipement.importation;

import fr.mecadetect.erreur.DonneesInvalidesException;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/// Import du parc d'équipements : lance le job Spring Batch et bâtit le rapport.
///
/// Le fichier reçu est copié dans un fichier temporaire, que le job lit puis que le service
/// supprime. Le lancement est synchrone : le rapport est renvoyé dès la fin du job.
///
/// Pas de `@Transactional` : le job ouvre ses propres transactions, une par lot.
@Service
@RequiredArgsConstructor
public class ImportEquipementsService {

    private final JobOperator jobOperator;
    private final Job importEquipementsJob;

    /// Importe les équipements du fichier.
    ///
    /// `synchronized` : les métadonnées du job sont gardées en mémoire, un import à la fois.
    /// L'import est une opération rare, réservée à l'admin.
    ///
    /// @param contenu contenu du fichier CSV, en UTF-8, avec une ligne d'en-tête
    /// @return le rapport : lignes lues, équipements créés, lignes rejetées et leur motif
    /// @throws DonneesInvalidesException si le fichier ne contient aucune ligne de données,
    ///                                   ou s'il compte trop de lignes rejetées
    public synchronized ResultatImport importer(InputStream contenu) {
        Path fichier = copierDansFichierTemporaire(contenu);
        try {
            return rapport(lancer(fichier));
        } finally {
            supprimer(fichier);
        }
    }

    private JobExecution lancer(Path fichier) {
        JobParameters parametres = new JobParametersBuilder()
                .addString(ConfigurationImportEquipements.PARAMETRE_FICHIER, fichier.toAbsolutePath().toString())
                // Chaque import est une nouvelle instance du job, même pour un fichier identique
                .addString("idImport", UUID.randomUUID().toString())
                .toJobParameters();
        try {
            return jobOperator.start(importEquipementsJob, parametres);
        } catch (Exception e) {
            throw new IllegalStateException("Le job d'import n'a pas pu être lancé.", e);
        }
    }

    private static ResultatImport rapport(JobExecution execution) {
        long lues = 0;
        long importes = 0;
        long rejetes = 0;
        List<RejetImport> rejets = new ArrayList<>();
        for (var etape : execution.getStepExecutions()) {
            rejetes += etape.getReadSkipCount() + etape.getProcessSkipCount() + etape.getWriteSkipCount();
            lues += etape.getReadCount() + etape.getReadSkipCount();
            importes += etape.getWriteCount();
            @SuppressWarnings("unchecked")
            var rejetsEtape = (List<RejetImport>) etape.getExecutionContext().get(CollecteurRejets.CLE_REJETS);
            if (rejetsEtape != null) {
                rejets.addAll(rejetsEtape);
            }
        }

        if (execution.getStatus() != BatchStatus.COMPLETED) {
            if (rejetes >= ConfigurationImportEquipements.LIMITE_REJETS) {
                throw new DonneesInvalidesException("TROP_DE_REJETS", "Import interrompu : plus de "
                        + ConfigurationImportEquipements.LIMITE_REJETS + " lignes rejetées, vérifiez le format du fichier. "
                        + importes + " équipement(s) avaient déjà été importés.");
            }
            throw new IllegalStateException("L'import a échoué : " + execution.getAllFailureExceptions());
        }
        if (lues == 0) {
            throw new DonneesInvalidesException("FICHIER_VIDE",
                    "Le fichier ne contient aucune ligne de données sous la ligne d'en-tête.");
        }

        rejets.sort(Comparator.comparingLong(RejetImport::ligne));
        return new ResultatImport(lues, importes, rejetes, List.copyOf(rejets));
    }

    private static Path copierDansFichierTemporaire(InputStream contenu) {
        Path fichier = null;
        try {
            fichier = Files.createTempFile("import-equipements-", ".csv");
            Files.copy(contenu, fichier, StandardCopyOption.REPLACE_EXISTING);
            return fichier;
        } catch (IOException e) {
            if (fichier != null) {
                supprimer(fichier);
            }
            throw new UncheckedIOException("Le fichier reçu n'a pas pu être enregistré.", e);
        }
    }

    private static void supprimer(Path fichier) {
        try {
            Files.deleteIfExists(fichier);
        } catch (IOException e) {
            // Sans conséquence : le dossier temporaire est nettoyé par le système
        }
    }
}
