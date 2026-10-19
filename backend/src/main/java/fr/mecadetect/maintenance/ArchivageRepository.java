package fr.mecadetect.maintenance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Types;
import java.time.LocalDate;
import java.util.Map;

/**
 * Appel de PKG_MAINTENANCE.ARCHIVER_INTERVENTIONS (UC-DB4) par SimpleJdbcCall.
 *
 * Les paramètres sont déclarés explicitement, dans l'ordre de la procédure : pas de lecture
 * des métadonnées du dictionnaire Oracle à chaque démarrage, et le type BOOLEAN de p_commit
 * est lié tel quel (BOOLEAN natif depuis Oracle 23ai), comme la date limite en LocalDate.
 *
 * Commit par lot seulement hors transaction Spring : en production, le service n'est pas
 * transactionnel et chaque lot archivé est acquis. Si l'appelant a ouvert une transaction
 * (les tests @Transactional), c'est lui qui décide : rien n'est validé par le package.
 */
@Repository
public class ArchivageRepository {

    private static final String DATE_LIMITE = "P_DATE_LIMITE";
    private static final String INTERVENTIONS = "P_INTERVENTIONS";
    private static final String HISTORIQUES = "P_HISTORIQUES";
    private static final String TAILLE_LOT = "P_TAILLE_LOT";
    private static final String COMMIT = "P_COMMIT";

    private final SimpleJdbcCall archiverInterventions;

    public ArchivageRepository(JdbcTemplate jdbc) {
        this.archiverInterventions = new SimpleJdbcCall(jdbc)
                .withCatalogName("PKG_MAINTENANCE")          // pour Oracle, le « catalogue » est le package
                .withProcedureName("ARCHIVER_INTERVENTIONS")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter(DATE_LIMITE, Types.DATE),
                        new SqlOutParameter(INTERVENTIONS, Types.NUMERIC),
                        new SqlOutParameter(HISTORIQUES, Types.NUMERIC),
                        new SqlParameter(TAILLE_LOT, Types.INTEGER),
                        new SqlParameter(COMMIT, Types.BOOLEAN));
    }

    public ResultatArchivage archiver(LocalDate dateLimite, int tailleLot) {
        boolean commitParLot = !TransactionSynchronizationManager.isActualTransactionActive();
        Map<String, Object> sortie = archiverInterventions.execute(new MapSqlParameterSource()
                .addValue(DATE_LIMITE, dateLimite)   // LocalDate lié tel quel : le pilote Oracle gère java.time
                .addValue(TAILLE_LOT, tailleLot)
                .addValue(COMMIT, commitParLot));
        return new ResultatArchivage(nombre(sortie, INTERVENTIONS), nombre(sortie, HISTORIQUES));
    }

    private static long nombre(Map<String, Object> sortie, String parametre) {
        return ((Number) sortie.get(parametre)).longValue();
    }
}