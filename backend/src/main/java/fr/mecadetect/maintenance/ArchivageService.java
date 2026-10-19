package fr.mecadetect.maintenance;

import fr.mecadetect.erreur.DonneesInvalidesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

/**
 * Archivage des interventions closes (UC-DB4, tâche 4.6).
 *
 * Volontairement sans @Transactional : un archivage porte sur des centaines de milliers
 * de lignes, le package valide lot par lot pour ne pas tout retenir dans une seule transaction.
 */
@Service
public class ArchivageService {

    private static final Logger LOG = LoggerFactory.getLogger(ArchivageService.class);

    /** La dernière année reste en ligne : planification, historique d'équipement, reporting. */
    static final Period CONSERVATION_MINIMALE = Period.ofYears(1);
    static final int TAILLE_LOT = 10_000;

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ArchivageRepository repository;
    private final Clock clock;

    public ArchivageService(ArchivageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** @return le nombre d'interventions archivées */
    public long archiver(LocalDate dateLimite) {
        LocalDate auPlusTard = LocalDate.now(clock).minus(CONSERVATION_MINIMALE);
        if (dateLimite.isAfter(auPlusTard)) {
            throw new DonneesInvalidesException("DATE_ARCHIVAGE_INVALIDE",
                    "La date limite d'archivage doit être au plus tard le " + auPlusTard.format(FORMAT)
                            + " : la dernière année reste consultable.");
        }

        long debut = System.nanoTime();
        ResultatArchivage resultat = repository.archiver(dateLimite, TAILLE_LOT);
        long dureeMs = (System.nanoTime() - debut) / 1_000_000;
        // Arguments calculés (date formatée) seulement si le message sera vraiment écrit
        if (LOG.isInfoEnabled()) {
            LOG.info("Archivage avant le {} : {} interventions et {} lignes d'historique en {} ms",
                    dateLimite.format(FORMAT), resultat.interventions(), resultat.historiques(), dureeMs);
        }
        return resultat.interventions();
    }
}