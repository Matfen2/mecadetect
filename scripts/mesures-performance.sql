-- =====================================================================
-- MécaDétect - mesures de performance (tâche 4.4, UC-DB3)
--
-- Usage, dans le conteneur :  @/tmp/mesures-performance.sql avant
--                             @/tmp/mesures-performance.sql apres
-- Le résultat est aussi écrit dans /tmp/mesures-<avant|apres>.txt
--
-- Pour chaque requête critique de l'application :
--   1. le plan d'exécution estimé (EXPLAIN PLAN + DBMS_XPLAN) ;
--   2. le temps moyen sur 20 exécutions, après une exécution de chauffe
--      (les données sont alors en mémoire : on mesure le travail d'Oracle,
--      pas la lecture disque).
--
-- Les requêtes reproduisent celles générées par Hibernate depuis la tâche 4.4 (recherche
-- dynamique), paramètres compris :
-- dans les blocs PL/SQL, les variables sont transmises comme des binds,
-- exactement comme les « ? » de JDBC.
-- =====================================================================
SET VERIFY OFF
SET FEEDBACK OFF
SET LINESIZE 200
SET PAGESIZE 1000
SET SERVEROUTPUT ON SIZE UNLIMITED
COLUMN plan_table_output FORMAT A200

SPOOL /tmp/mesures-&1..txt

-- ---------------------------------------------------------------- jeux de valeurs
COLUMN id_int  NEW_VALUE id_int  NOPRINT
COLUMN id_eq   NEW_VALUE id_eq   NOPRINT
COLUMN id_tech NEW_VALUE id_tech NOPRINT
COLUMN id_tech2 NEW_VALUE id_tech2 NOPRINT
-- Une intervention terminée prise au milieu de la table, l'équipement le plus chargé, Lucas
SELECT MIN(id_intervention) + 250000 AS id_int FROM intervention;
SELECT id_equipement AS id_eq FROM equipement WHERE reference = 'PRS-001';
SELECT id_utilisateur AS id_tech FROM utilisateur WHERE email = 'technicien@mecadetect.fr';
-- Un technicien généré, sans intervention en cours (pire cas de RG-04)
SELECT MAX(u.id_utilisateur) AS id_tech2 FROM utilisateur u
 WHERE u.role = 'TECHNICIEN'
   AND NOT EXISTS (SELECT 1 FROM intervention i WHERE i.id_technicien = u.id_utilisateur AND i.statut = 'EN_COURS');

PROMPT
PROMPT ======================================================================
PROMPT  Mesures « &1 » - intervention &id_int, équipement PRS-001 (&id_eq), technicien &id_tech
PROMPT ======================================================================
SELECT 'Index sur INTERVENTION et HISTORIQUE_STATUT : ' || COUNT(*) AS plan_table_output
  FROM user_indexes WHERE table_name IN ('INTERVENTION', 'HISTORIQUE_STATUT');
SELECT '  ' || index_name || ' (' || LISTAGG(column_name, ', ') WITHIN GROUP (ORDER BY column_position) || ')' AS plan_table_output
  FROM user_ind_columns WHERE table_name IN ('INTERVENTION', 'HISTORIQUE_STATUT')
 GROUP BY index_name ORDER BY index_name;

-- ---------------------------------------------------------------- plans d'exécution
-- EXPLAIN PLAN ne lit pas les valeurs : il faut seulement déclarer les binds pour SQL*Plus
VARIABLE id NUMBER
VARIABLE statut VARCHAR2(20)
VARIABLE priorite VARCHAR2(10)
VARIABLE tech NUMBER
VARIABLE eq NUMBER
VARIABLE depuis VARCHAR2(30)
VARIABLE avant VARCHAR2(30)
PROMPT
PROMPT ---------- Q1. Détail : historique des statuts d'une intervention
EXPLAIN PLAN SET STATEMENT_ID = 'Q1' FOR
SELECT h.id_historique, h.ancien_statut, h.nouveau_statut, h.date_changement, u.nom, u.prenom
  FROM historique_statut h JOIN utilisateur u ON u.id_utilisateur = h.id_auteur
 WHERE h.id_intervention = :id
 ORDER BY h.date_changement, h.id_historique;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q1', 'BASIC +ROWS +COST +PREDICATE'));

-- Depuis la tâche 4.4, la recherche est construite dynamiquement : seuls les filtres demandés
-- apparaissent dans le SQL. Chaque cas a donc sa propre requête (page + comptage sans jointure).
PROMPT
PROMPT ---------- Q2a. Liste sans filtre (page 1, 20 lignes)
EXPLAIN PLAN SET STATEMENT_ID = 'Q2a' FOR
SELECT i.id_intervention, i.numero, i.titre, i.priorite, i.statut, i.date_demande, i.date_planifiee,
       e.reference, e.nom, t.id_utilisateur, t.prenom, t.nom
  FROM intervention i
  JOIN equipement e ON e.id_equipement = i.id_equipement
  LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
 ORDER BY i.date_demande DESC, i.id_intervention DESC
 OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q2a', 'BASIC +ROWS +COST +PREDICATE'));
EXPLAIN PLAN SET STATEMENT_ID = 'Q2aC' FOR
SELECT COUNT(i.id_intervention) FROM intervention i;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q2aC', 'BASIC +ROWS +COST +PREDICATE'));
PROMPT
PROMPT ---------- Q2b. Liste filtrée sur un statut
EXPLAIN PLAN SET STATEMENT_ID = 'Q2b' FOR
SELECT i.id_intervention, i.numero, i.titre, i.priorite, i.statut, i.date_demande, i.date_planifiee,
       e.reference, e.nom, t.id_utilisateur, t.prenom, t.nom
  FROM intervention i
  JOIN equipement e ON e.id_equipement = i.id_equipement
  LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
 WHERE i.statut = :statut
 ORDER BY i.date_demande DESC, i.id_intervention DESC
 OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q2b', 'BASIC +ROWS +COST +PREDICATE'));
EXPLAIN PLAN SET STATEMENT_ID = 'Q2bC' FOR
SELECT COUNT(i.id_intervention) FROM intervention i
 WHERE i.statut = :statut;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q2bC', 'BASIC +ROWS +COST +PREDICATE'));
PROMPT
PROMPT ---------- Q2c. Historique d'un équipement
EXPLAIN PLAN SET STATEMENT_ID = 'Q2c' FOR
SELECT i.id_intervention, i.numero, i.titre, i.priorite, i.statut, i.date_demande, i.date_planifiee,
       e.reference, e.nom, t.id_utilisateur, t.prenom, t.nom
  FROM intervention i
  JOIN equipement e ON e.id_equipement = i.id_equipement
  LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
 WHERE i.id_equipement = :eq
 ORDER BY i.date_demande DESC, i.id_intervention DESC
 OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q2c', 'BASIC +ROWS +COST +PREDICATE'));
EXPLAIN PLAN SET STATEMENT_ID = 'Q2cC' FOR
SELECT COUNT(i.id_intervention) FROM intervention i
 WHERE i.id_equipement = :eq;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q2cC', 'BASIC +ROWS +COST +PREDICATE'));
PROMPT
PROMPT ---------- Q2d. Liste filtrée sur un technicien
EXPLAIN PLAN SET STATEMENT_ID = 'Q2d' FOR
SELECT i.id_intervention, i.numero, i.titre, i.priorite, i.statut, i.date_demande, i.date_planifiee,
       e.reference, e.nom, t.id_utilisateur, t.prenom, t.nom
  FROM intervention i
  JOIN equipement e ON e.id_equipement = i.id_equipement
  LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
 WHERE i.id_technicien = :tech
 ORDER BY i.date_demande DESC, i.id_intervention DESC
 OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q2d', 'BASIC +ROWS +COST +PREDICATE'));
EXPLAIN PLAN SET STATEMENT_ID = 'Q2dC' FOR
SELECT COUNT(i.id_intervention) FROM intervention i
 WHERE i.id_technicien = :tech;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q2dC', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT
PROMPT ---------- Q3. Mes interventions d'un technicien (50 lignes, en cours d'abord)
EXPLAIN PLAN SET STATEMENT_ID = 'Q3' FOR
SELECT i.id_intervention, i.numero, i.titre, i.priorite, i.statut, i.date_demande, i.date_planifiee,
       e.reference, e.nom, t.id_utilisateur, t.prenom, t.nom
  FROM intervention i
  JOIN equipement e ON e.id_equipement = i.id_equipement
  JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
 WHERE t.id_utilisateur = :tech AND i.statut IN ('PLANIFIEE', 'EN_COURS')
 ORDER BY CASE WHEN i.statut = 'EN_COURS' THEN 0 ELSE 1 END, i.date_planifiee, i.id_intervention
 OFFSET 0 ROWS FETCH FIRST 50 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q3', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT
PROMPT ---------- Q4. RG-04 : le technicien a-t-il déjà une intervention en cours ?
EXPLAIN PLAN SET STATEMENT_ID = 'Q4' FOR
SELECT i.id_intervention FROM intervention i
 WHERE i.id_technicien = :tech AND i.statut = 'EN_COURS'
 FETCH FIRST 1 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q4', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT
PROMPT ---------- Q5. RG-05 : l'équipement a-t-il encore une intervention ouverte ?
EXPLAIN PLAN SET STATEMENT_ID = 'Q5' FOR
SELECT i.id_intervention FROM intervention i
 WHERE i.id_equipement = :eq AND i.statut IN ('DEMANDEE', 'PLANIFIEE', 'EN_COURS')
 FETCH FIRST 1 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q5', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT
PROMPT ---------- Q6. Reporting : top 10 des équipements sur 12 mois
EXPLAIN PLAN SET STATEMENT_ID = 'Q6' FOR
SELECT rang, reference, nom, nombre_pannes
  FROM (SELECT e.reference, e.nom, COUNT(*) AS nombre_pannes, RANK() OVER (ORDER BY COUNT(*) DESC) AS rang
          FROM intervention i JOIN equipement e ON e.id_equipement = i.id_equipement
         WHERE i.date_demande >= :depuis AND i.date_demande < :avant AND i.statut <> 'ANNULEE'
         GROUP BY e.id_equipement, e.reference, e.nom)
 WHERE rang <= 10 ORDER BY rang, reference;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'Q6', 'BASIC +ROWS +COST +PREDICATE'));

-- ---------------------------------------------------------------- temps d'exécution
PROMPT
PROMPT ======================================================================
PROMPT  Temps moyen sur 20 exécutions (après une exécution de chauffe)
PROMPT ======================================================================
DECLARE
    c_repetitions CONSTANT PLS_INTEGER := 20;
    v_id_int  NUMBER := &id_int;
    v_id_eq   NUMBER := &id_eq;
    v_id_tech NUMBER := &id_tech;
    v_id_tech2 NUMBER := &id_tech2;
    v_lignes  NUMBER;
    v_nombre  NUMBER;

    PROCEDURE afficher(p_libelle VARCHAR2, p_centiemes NUMBER, p_lignes VARCHAR2) IS
    BEGIN
        DBMS_OUTPUT.PUT_LINE(RPAD(p_libelle, 52, '.')
            || LPAD(TO_CHAR(p_centiemes * 10 / c_repetitions, 'FM99990.0'), 9) || ' ms   '
            || p_lignes || ' ligne(s)');
    END;

    -- Q2 : la recherche dynamique ; un filtre absent n'apparaît pas dans le SQL (tâche 4.4)
    PROCEDURE recherche(p_libelle VARCHAR2, p_statut VARCHAR2, p_tech NUMBER, p_eq NUMBER) IS
        v_debut NUMBER;
    BEGIN
        FOR k IN 0 .. c_repetitions LOOP
            IF k = 1 THEN v_debut := DBMS_UTILITY.GET_TIME; END IF;
            v_lignes := 0;
            IF p_statut IS NOT NULL THEN
                FOR r IN (SELECT i.id_intervention, i.numero, i.titre, e.reference, t.nom
                            FROM intervention i
                            JOIN equipement e ON e.id_equipement = i.id_equipement
                            LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
                           WHERE i.statut = p_statut
                           ORDER BY i.date_demande DESC, i.id_intervention DESC
                           OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY) LOOP
                    v_lignes := v_lignes + 1;
                END LOOP;
                SELECT COUNT(i.id_intervention) INTO v_nombre FROM intervention i WHERE i.statut = p_statut;
            ELSIF p_tech IS NOT NULL THEN
                FOR r IN (SELECT i.id_intervention, i.numero, i.titre, e.reference, t.nom
                            FROM intervention i
                            JOIN equipement e ON e.id_equipement = i.id_equipement
                            LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
                           WHERE i.id_technicien = p_tech
                           ORDER BY i.date_demande DESC, i.id_intervention DESC
                           OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY) LOOP
                    v_lignes := v_lignes + 1;
                END LOOP;
                SELECT COUNT(i.id_intervention) INTO v_nombre FROM intervention i WHERE i.id_technicien = p_tech;
            ELSIF p_eq IS NOT NULL THEN
                FOR r IN (SELECT i.id_intervention, i.numero, i.titre, e.reference, t.nom
                            FROM intervention i
                            JOIN equipement e ON e.id_equipement = i.id_equipement
                            LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
                           WHERE i.id_equipement = p_eq
                           ORDER BY i.date_demande DESC, i.id_intervention DESC
                           OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY) LOOP
                    v_lignes := v_lignes + 1;
                END LOOP;
                SELECT COUNT(i.id_intervention) INTO v_nombre FROM intervention i WHERE i.id_equipement = p_eq;
            ELSE
                FOR r IN (SELECT i.id_intervention, i.numero, i.titre, e.reference, t.nom
                            FROM intervention i
                            JOIN equipement e ON e.id_equipement = i.id_equipement
                            LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
                           ORDER BY i.date_demande DESC, i.id_intervention DESC
                           OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY) LOOP
                    v_lignes := v_lignes + 1;
                END LOOP;
                SELECT COUNT(i.id_intervention) INTO v_nombre FROM intervention i;
            END IF;
        END LOOP;
        afficher(p_libelle, DBMS_UTILITY.GET_TIME - v_debut, v_lignes || ' sur ' || v_nombre);
    END;

    PROCEDURE q1 IS
        v_debut NUMBER;
    BEGIN
        FOR k IN 0 .. c_repetitions LOOP
            IF k = 1 THEN v_debut := DBMS_UTILITY.GET_TIME; END IF;
            v_lignes := 0;
            FOR r IN (SELECT h.id_historique, h.ancien_statut, h.nouveau_statut, h.date_changement, u.nom, u.prenom
                        FROM historique_statut h JOIN utilisateur u ON u.id_utilisateur = h.id_auteur
                       WHERE h.id_intervention = v_id_int
                       ORDER BY h.date_changement, h.id_historique) LOOP
                v_lignes := v_lignes + 1;
            END LOOP;
        END LOOP;
        afficher('Q1  Historique d''une intervention (détail)', DBMS_UTILITY.GET_TIME - v_debut, v_lignes);
    END;

    PROCEDURE q3 IS
        v_debut NUMBER;
    BEGIN
        FOR k IN 0 .. c_repetitions LOOP
            IF k = 1 THEN v_debut := DBMS_UTILITY.GET_TIME; END IF;
            v_lignes := 0;
            FOR r IN (SELECT i.id_intervention, i.numero, i.titre, i.statut, e.reference, t.nom
                        FROM intervention i
                        JOIN equipement e ON e.id_equipement = i.id_equipement
                        JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
                       WHERE t.id_utilisateur = v_id_tech AND i.statut IN ('PLANIFIEE', 'EN_COURS')
                       ORDER BY CASE WHEN i.statut = 'EN_COURS' THEN 0 ELSE 1 END, i.date_planifiee, i.id_intervention
                       OFFSET 0 ROWS FETCH FIRST 50 ROWS ONLY) LOOP
                v_lignes := v_lignes + 1;
            END LOOP;
        END LOOP;
        afficher('Q3  Mes interventions (technicien)', DBMS_UTILITY.GET_TIME - v_debut, v_lignes);
    END;

    PROCEDURE q4_q5 IS
        v_debut NUMBER;
    BEGIN
        FOR k IN 0 .. c_repetitions LOOP
            IF k = 1 THEN v_debut := DBMS_UTILITY.GET_TIME; END IF;
            SELECT COUNT(*) INTO v_lignes FROM (
                SELECT i.id_intervention FROM intervention i
                 WHERE i.id_technicien = v_id_tech AND i.statut = 'EN_COURS' FETCH FIRST 1 ROWS ONLY);
        END LOOP;
        afficher('Q4  RG-04 intervention en cours du technicien', DBMS_UTILITY.GET_TIME - v_debut, v_lignes);

        -- Pire cas : un technicien sans intervention en cours, il faut tout examiner pour répondre « non »
        FOR k IN 0 .. c_repetitions LOOP
            IF k = 1 THEN v_debut := DBMS_UTILITY.GET_TIME; END IF;
            SELECT COUNT(*) INTO v_lignes FROM (
                SELECT i.id_intervention FROM intervention i
                 WHERE i.id_technicien = v_id_tech2 AND i.statut = 'EN_COURS' FETCH FIRST 1 ROWS ONLY);
        END LOOP;
        afficher('Q4b RG-04, technicien sans intervention en cours', DBMS_UTILITY.GET_TIME - v_debut, v_lignes);

        FOR k IN 0 .. c_repetitions LOOP
            IF k = 1 THEN v_debut := DBMS_UTILITY.GET_TIME; END IF;
            SELECT COUNT(*) INTO v_lignes FROM (
                SELECT i.id_intervention FROM intervention i
                 WHERE i.id_equipement = v_id_eq AND i.statut IN ('DEMANDEE', 'PLANIFIEE', 'EN_COURS')
                 FETCH FIRST 1 ROWS ONLY);
        END LOOP;
        afficher('Q5  RG-05 intervention ouverte sur l''équipement', DBMS_UTILITY.GET_TIME - v_debut, v_lignes);
    END;

    PROCEDURE q6 IS
        v_debut  NUMBER;
        v_depuis TIMESTAMP := SYS_EXTRACT_UTC(SYSTIMESTAMP) - INTERVAL '12' MONTH;
        v_avant  TIMESTAMP := SYS_EXTRACT_UTC(SYSTIMESTAMP);
    BEGIN
        FOR k IN 0 .. c_repetitions LOOP
            IF k = 1 THEN v_debut := DBMS_UTILITY.GET_TIME; END IF;
            v_lignes := 0;
            FOR r IN (SELECT rang FROM (
                          SELECT RANK() OVER (ORDER BY COUNT(*) DESC) AS rang
                            FROM intervention i JOIN equipement e ON e.id_equipement = i.id_equipement
                           WHERE i.date_demande >= v_depuis AND i.date_demande < v_avant AND i.statut <> 'ANNULEE'
                           GROUP BY e.id_equipement, e.reference, e.nom)
                       WHERE rang <= 10) LOOP
                v_lignes := v_lignes + 1;
            END LOOP;
        END LOOP;
        afficher('Q6  Reporting : top 10 des équipements (12 mois)', DBMS_UTILITY.GET_TIME - v_debut, v_lignes);
    END;
BEGIN
    q1;
    recherche('Q2a Liste sans filtre, page 1 + comptage', NULL, NULL, NULL);
    recherche('Q2b Liste filtrée statut PLANIFIEE + comptage', 'PLANIFIEE', NULL, NULL);
    recherche('Q2c Historique de PRS-001, page 1 + comptage', NULL, NULL, v_id_eq);
    recherche('Q2d Liste filtrée technicien Lucas + comptage', NULL, v_id_tech, NULL);
    q3;
    q4_q5;
    q6;
END;
/

SPOOL OFF
EXIT