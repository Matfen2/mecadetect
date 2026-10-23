-- =====================================================================
-- MécaDétect - requêtes SQL réellement envoyées par l'API (tâche 4.8)
--
-- Après une campagne Gatling, lit dans V$SQL les requêtes exécutées par
-- l'application (texte exact généré par Hibernate), leur temps moyen et le
-- plan d'exécution réellement utilisé. À exécuter en SYSTEM (V$SQL n'est pas
-- accessible au compte applicatif) :
--
--   docker cp scripts/sql-reel-application.sql mecadetect-oracle:/tmp/
--   docker exec -it mecadetect-oracle bash -c 'sqlplus -s system/$ORACLE_PASSWORD@FREEPDB1 @/tmp/sql-reel-application.sql'
--   docker cp mecadetect-oracle:/tmp/sql-reel-application.txt docs/
-- =====================================================================
SET LINESIZE 250
SET PAGESIZE 500
SET LONG 10000
SET TRIMSPOOL ON
COLUMN sql_texte FORMAT A110 WORD_WRAPPED
COLUMN plan_table_output FORMAT A200

SPOOL /tmp/sql-reel-application.txt

PROMPT === Requêtes de l'application, de la plus coûteuse (temps cumulé) à la moins coûteuse
SELECT sql_id,
       executions                                                  AS exec,
       ROUND(elapsed_time / executions / 1000, 1)                  AS ms_moy,
       ROUND(buffer_gets / executions)                             AS blocs_moy,
       ROUND(rows_processed / executions)                          AS lignes_moy,
       SUBSTR(sql_fulltext, 1, 400)                                AS sql_texte
  FROM v$sql
 WHERE parsing_schema_name = 'MECADETECT'
   AND executions > 0
   AND command_type = 3            -- SELECT uniquement
 ORDER BY elapsed_time DESC
 FETCH FIRST 12 ROWS ONLY;

PROMPT === Plans réellement utilisés par les 4 requêtes les plus coûteuses
SELECT p.plan_table_output
  FROM (SELECT sql_id, child_number
          FROM v$sql
         WHERE parsing_schema_name = 'MECADETECT'
           AND executions > 0
           AND command_type = 3
         ORDER BY elapsed_time DESC
         FETCH FIRST 4 ROWS ONLY) s,
       TABLE(DBMS_XPLAN.DISPLAY_CURSOR(s.sql_id, s.child_number, 'TYPICAL')) p;

SPOOL OFF
EXIT