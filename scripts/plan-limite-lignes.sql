-- =====================================================================
-- MécaDétect - expérience : pourquoi la recherche Criteria lit toute la table (tâche 4.8)
--
-- Hibernate envoie « OFFSET :1 ROWS FETCH FIRST :2 ROWS ONLY » avec des valeurs liées.
-- Oracle ne sait donc pas, en calculant le plan, qu'on ne veut que 20 lignes : il optimise
-- pour renvoyer TOUTES les lignes (parcours complet + tri) au lieu de lire l'index trié
-- et de s'arrêter après 20 lignes. On compare, sur 20 exécutions chacune :
--   A / C : la requête telle qu'Hibernate l'envoie (sans filtre / filtre équipement)
--   B / D : la même avec l'indication /*+ FIRST_ROWS(20) */
--
-- À exécuter en SYSTEM (lecture de V$SQL pour les plans réels) :
--   Get-Content scripts\plan-limite-lignes.sql -Raw | docker exec -i mecadetect-oracle bash -c 'cat > /tmp/plan-limite-lignes-v1.sql'
--   docker exec -it mecadetect-oracle bash -c 'sqlplus -s system/$ORACLE_PASSWORD@FREEPDB1 @/tmp/plan-limite-lignes-v1.sql'
-- =====================================================================
SET SERVEROUTPUT ON
SET LINESIZE 200
SET PAGESIZE 500
COLUMN plan_table_output FORMAT A190
ALTER SESSION SET CURRENT_SCHEMA = MECADETECT;

PROMPT === Temps moyen sur 20 exécutions (page 1, 20 lignes)
DECLARE
    v_debut  NUMBER;
    v_decal  NUMBER := 0;     -- valeurs liées, comme celles envoyées par Hibernate
    v_nombre NUMBER := 20;
    v_equip  NUMBER;

    PROCEDURE afficher(p_nom VARCHAR2) IS
    BEGIN
        DBMS_OUTPUT.PUT_LINE(RPAD(p_nom, 55) || TO_CHAR((DBMS_UTILITY.GET_TIME - v_debut) * 10 / 20, '990.0') || ' ms');
    END;
BEGIN
    -- L'équipement qui a le plus d'interventions : le cas le plus défavorable
    SELECT id_equipement INTO v_equip
      FROM intervention GROUP BY id_equipement ORDER BY COUNT(*) DESC FETCH FIRST 1 ROW ONLY;

    v_debut := DBMS_UTILITY.GET_TIME;
    FOR k IN 1 .. 20 LOOP
        FOR r IN (SELECT 'VARIANTE_A' v, i.id_intervention, i.numero, i.titre, i.priorite, i.statut, i.date_demande,
                         i.date_planifiee, e.reference, e.nom AS equipement, t.id_utilisateur, t.prenom, t.nom
                    FROM intervention i
                    JOIN equipement e ON e.id_equipement = i.id_equipement
                    LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
                   ORDER BY i.date_demande DESC, i.id_intervention DESC
                  OFFSET v_decal ROWS FETCH FIRST v_nombre ROWS ONLY) LOOP NULL; END LOOP;
    END LOOP;
    afficher('A  sans filtre, comme Hibernate');

    v_debut := DBMS_UTILITY.GET_TIME;
    FOR k IN 1 .. 20 LOOP
        FOR r IN (SELECT /*+ FIRST_ROWS(20) */ 'VARIANTE_B' v, i.id_intervention, i.numero, i.titre, i.priorite,
                         i.statut, i.date_demande, i.date_planifiee, e.reference, e.nom AS equipement,
                         t.id_utilisateur, t.prenom, t.nom
                    FROM intervention i
                    JOIN equipement e ON e.id_equipement = i.id_equipement
                    LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
                   ORDER BY i.date_demande DESC, i.id_intervention DESC
                  OFFSET v_decal ROWS FETCH FIRST v_nombre ROWS ONLY) LOOP NULL; END LOOP;
    END LOOP;
    afficher('B  sans filtre, avec FIRST_ROWS(20)');

    v_debut := DBMS_UTILITY.GET_TIME;
    FOR k IN 1 .. 20 LOOP
        FOR r IN (SELECT 'VARIANTE_C' v, i.id_intervention, i.numero, i.titre, i.priorite, i.statut, i.date_demande,
                         i.date_planifiee, e.reference, e.nom AS equipement, t.id_utilisateur, t.prenom, t.nom
                    FROM intervention i
                    JOIN equipement e ON e.id_equipement = i.id_equipement
                    LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
                   WHERE i.id_equipement = v_equip
                   ORDER BY i.date_demande DESC, i.id_intervention DESC
                  OFFSET v_decal ROWS FETCH FIRST v_nombre ROWS ONLY) LOOP NULL; END LOOP;
    END LOOP;
    afficher('C  filtre équipement, comme Hibernate');

    v_debut := DBMS_UTILITY.GET_TIME;
    FOR k IN 1 .. 20 LOOP
        FOR r IN (SELECT /*+ FIRST_ROWS(20) */ 'VARIANTE_D' v, i.id_intervention, i.numero, i.titre, i.priorite,
                         i.statut, i.date_demande, i.date_planifiee, e.reference, e.nom AS equipement,
                         t.id_utilisateur, t.prenom, t.nom
                    FROM intervention i
                    JOIN equipement e ON e.id_equipement = i.id_equipement
                    LEFT JOIN utilisateur t ON t.id_utilisateur = i.id_technicien
                   WHERE i.id_equipement = v_equip
                   ORDER BY i.date_demande DESC, i.id_intervention DESC
                  OFFSET v_decal ROWS FETCH FIRST v_nombre ROWS ONLY) LOOP NULL; END LOOP;
    END LOOP;
    afficher('D  filtre équipement, avec FIRST_ROWS(20)');
END;
/

PROMPT === Plans réellement utilisés (A, B, C, D)
SELECT p.plan_table_output
  FROM (SELECT sql_id, child_number, sql_text
          FROM v$sql
         WHERE sql_text LIKE '%''VARIANTE\__''%' ESCAPE '\'
           AND UPPER(sql_text) NOT LIKE '%V$SQL%') s,
       TABLE(DBMS_XPLAN.DISPLAY_CURSOR(s.sql_id, s.child_number, 'BASIC +ROWS +PREDICATE')) p;

EXIT