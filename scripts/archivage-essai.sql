-- =====================================================================
-- MécaDétect - essai d'archivage sur le volume de 500 000 interventions (UC-DB4)
--
-- Archivage « à blanc » : p_commit => FALSE puis ROLLBACK. On mesure le temps
-- et les volumes sans perdre les données (utiles pour les tests de charge de l'étape 6).
-- Seul le journal est conservé (transaction autonome).
--
-- Lancement :
--   docker cp scripts/archivage-essai.sql mecadetect-oracle:/tmp/
--   docker exec -it mecadetect-oracle bash -c 'sqlplus -s $APP_USER/$APP_USER_PASSWORD@FREEPDB1 @/tmp/archivage-essai.sql'
-- =====================================================================
SET SERVEROUTPUT ON
SET LINESIZE 200
SET PAGESIZE 100
COLUMN statut FORMAT A10
COLUMN message FORMAT A60

PROMPT === Avant : interventions par statut, et celles closes depuis plus de 3 ans
SELECT statut, COUNT(*) AS nombre FROM intervention GROUP BY statut ORDER BY statut;
SELECT COUNT(*) AS archivables
  FROM intervention
 WHERE (statut = 'TERMINEE' AND date_fin < ADD_MONTHS(TRUNC(SYSDATE), -36))
    OR (statut = 'ANNULEE' AND date_demande < ADD_MONTHS(TRUNC(SYSDATE), -36));

PROMPT === Archivage à blanc des interventions closes depuis plus de 3 ans
DECLARE
    v_interventions NUMBER;
    v_historiques   NUMBER;
    v_debut         NUMBER := DBMS_UTILITY.GET_TIME;
BEGIN
    pkg_maintenance.archiver_interventions(
        p_date_limite   => ADD_MONTHS(TRUNC(SYSDATE), -36),
        p_interventions => v_interventions,
        p_historiques   => v_historiques,
        p_commit        => FALSE);
    DBMS_OUTPUT.PUT_LINE('Interventions archivées : ' || v_interventions);
    DBMS_OUTPUT.PUT_LINE('Historiques archivés    : ' || v_historiques);
    DBMS_OUTPUT.PUT_LINE('Durée                   : ' || (DBMS_UTILITY.GET_TIME - v_debut) / 100 || ' s');
END;
/

PROMPT === Pendant (avant annulation) : tables actives et archives
SELECT (SELECT COUNT(*) FROM intervention)              AS interventions,
       (SELECT COUNT(*) FROM intervention_archive)      AS interventions_archivees,
       (SELECT COUNT(*) FROM historique_statut)         AS historiques,
       (SELECT COUNT(*) FROM historique_statut_archive) AS historiques_archives
  FROM dual;

ROLLBACK;

PROMPT === Après ROLLBACK : les données sont intactes
SELECT COUNT(*) AS interventions FROM intervention;

PROMPT === Journal (conservé malgré le ROLLBACK)
SELECT id_journal, statut, interventions, historiques,
       ROUND(EXTRACT(SECOND FROM (date_fin - date_debut)) + 60 * EXTRACT(MINUTE FROM (date_fin - date_debut)), 1) AS duree_s,
       message
  FROM journal_archivage ORDER BY id_journal DESC FETCH FIRST 5 ROWS ONLY;

EXIT