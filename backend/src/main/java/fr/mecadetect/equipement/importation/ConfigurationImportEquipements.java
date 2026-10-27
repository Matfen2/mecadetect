package fr.mecadetect.equipement.importation;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.ZoneRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

/// Job Spring Batch d'import du parc d'équipements depuis un fichier CSV.
///
/// Un seul step, orienté lots (*chunk*) :
///
/// 1. **lecture** : le fichier est lu ligne par ligne et découpé en colonnes ([LecteurLigneEquipement]) ;
/// 2. **traitement** : chaque ligne est validée et devient un équipement ([ValidationLigneEquipement]) ;
/// 3. **écriture** : les équipements sont enregistrés par lots de 100, un lot par transaction.
///
/// Tolérance aux erreurs : une ligne illisible ou invalide est ignorée (*skip*) et consignée par
/// le [CollecteurRejets], sans bloquer les autres. Au-delà de 1 000 rejets, le fichier
/// n'est manifestement pas au bon format : le job s'arrête en échec.
@Configuration
public class ConfigurationImportEquipements {

    /// Nom du job, tel qu'il apparaît dans les métadonnées de Spring Batch.
    public static final String NOM_JOB = "importEquipements";

    /// Paramètre du job : chemin du fichier CSV à importer.
    public static final String PARAMETRE_FICHIER = "fichier";

    /// Équipements enregistrés par transaction.
    static final int TAILLE_LOT = 100;

    /// Nombre de rejets au-delà duquel l'import est interrompu.
    static final int LIMITE_REJETS = 1_000;

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /// @param jobRepository         dépôt des métadonnées des jobs
    /// @param importEquipementsStep step unique de l'import
    /// @return le job d'import du parc
    @Bean
    public Job importEquipementsJob(JobRepository jobRepository, Step importEquipementsStep) {
        return new JobBuilder(NOM_JOB, jobRepository)
                .start(importEquipementsStep)
                .build();
    }

    /// @param jobRepository         dépôt des métadonnées des jobs
    /// @param transactionManager    transactions JPA : un lot est enregistré entièrement ou pas du tout
    /// @param lecteur               lecteur du fichier CSV
    /// @param validation            validation des lignes
    /// @param equipementRepository  enregistrement des équipements
    /// @return le step de lecture, validation et écriture par lots
    @Bean
    public Step importEquipementsStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      FlatFileItemReader<LigneEquipementCsv> lecteur,
                                      ValidationLigneEquipement validation,
                                      EquipementRepository equipementRepository) {
        return new StepBuilder("importEquipementsStep", jobRepository)
                .<LigneEquipementCsv, Equipement>chunk(TAILLE_LOT)
                .transactionManager(transactionManager)
                .reader(lecteur)
                .processor(validation::valider)
                .writer(lot -> equipementRepository.saveAll(lot.getItems()))
                .faultTolerant()
                // Ligne illisible, ligne invalide, ou référence refusée par la base à l'écriture
                .skip(FlatFileParseException.class, LigneRejeteeException.class,
                        DataIntegrityViolationException.class)
                .skipLimit(LIMITE_REJETS)
                .skipListener(new CollecteurRejets())
                .build();
    }

    /// Lecteur du fichier, créé pour chaque exécution (`@StepScope`) : le chemin du fichier
    /// n'est connu qu'au lancement du job, dans ses paramètres.
    ///
    /// @param fichier chemin du fichier CSV, lu dans les paramètres du job
    /// @return le lecteur, qui ignore la ligne d'en-tête
    @Bean
    @StepScope
    public FlatFileItemReader<LigneEquipementCsv> lecteurEquipementsCsv(
            @Value("#{jobParameters['" + PARAMETRE_FICHIER + "']}") String fichier) {
        return new FlatFileItemReaderBuilder<LigneEquipementCsv>()
                .name("lecteurEquipementsCsv")
                .resource(new FileSystemResource(fichier))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .lineMapper(new LecteurLigneEquipement())
                .build();
    }

    /// Validation créée pour chaque exécution (`@StepScope`) : le référentiel est chargé une fois
    /// au début de l'import, et les références déjà vues ne valent que pour ce fichier.
    ///
    /// @param typeRepository       types d'équipement du référentiel
    /// @param zoneRepository       zones du référentiel
    /// @param equipementRepository vérification des références déjà utilisées
    /// @return la validation des lignes de cet import
    @Bean
    @StepScope
    public ValidationLigneEquipement validationLigneEquipement(TypeEquipementRepository typeRepository,
                                                               ZoneRepository zoneRepository,
                                                               EquipementRepository equipementRepository) {
        return new ValidationLigneEquipement(
                typeRepository.findAll(), zoneRepository.findAll(),
                equipementRepository::existsByReferenceIgnoreCase,
                LocalDate.now(PARIS));
    }
}
