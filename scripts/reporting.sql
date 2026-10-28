-- Requêtes de reporting (tâche 4.3), à lancer dans SQL*Plus sur le jeu de 500 000 interventions.
-- Ce sont les mêmes requêtes que ReportingRepository, avec la période des 12 derniers mois.
-- Les temps affichés (SET TIMING ON) sont la référence « avant index » de l'étape 4.
SET LINESIZE 200
SET PAGESIZE 100
SET TIMING ON
COLUMN reference FORMAT A12
COLUMN nom FORMAT A40
COLUMN technicien FORMAT A30

DEFINE depuis = "SYS_EXTRACT_UTC(SYSTIMESTAMP) - INTERVAL '12' MONTH"
DEFINE avant  = "SYS_EXTRACT_UTC(SYSTIMESTAMP)"

PROMPT === Top 10 des équipements en panne (RANK) ===
SELECT rang, reference, nom, nombre_pannes
  FROM (SELECT e.reference, e.nom, COUNT(*) AS nombre_pannes,
               RANK() OVER (ORDER BY COUNT(*) DESC) AS rang
          FROM intervention i
          JOIN equipement e ON e.id_equipement = i.id_equipement
         WHERE i.date_demande >= &depuis AND i.date_demande < &avant
           AND i.statut <> 'ANNULEE'
         GROUP BY e.id_equipement, e.reference, e.nom)
 WHERE rang <= 10
 ORDER BY rang, reference;

PROMPT === Évolution mensuelle (LAG) ===
WITH mois AS (
    SELECT ADD_MONTHS(TRUNC(&depuis, 'MM'), LEVEL - 1) AS debut
      FROM dual
   CONNECT BY ADD_MONTHS(TRUNC(&depuis, 'MM'), LEVEL - 1) < &avant
), comptes AS (
    SELECT TRUNC(date_demande, 'MM') AS debut, COUNT(*) AS nombre
      FROM intervention
     WHERE date_demande >= &depuis AND date_demande < &avant
     GROUP BY TRUNC(date_demande, 'MM')
)
SELECT TO_CHAR(m.debut, 'YYYY-MM') AS mois,
       NVL(c.nombre, 0) AS nombre,
       NVL(c.nombre, 0) - LAG(NVL(c.nombre, 0)) OVER (ORDER BY m.debut) AS variation
  FROM mois m LEFT JOIN comptes c ON c.debut = m.debut
 ORDER BY m.debut;

PROMPT === Temps moyen de résolution par priorité ===
SELECT priorite, COUNT(*) AS nombre,
       ROUND(AVG((CAST(date_fin AS DATE) - CAST(date_demande AS DATE)) * 24), 1) AS heures
  FROM intervention
 WHERE statut = 'TERMINEE' AND date_fin >= &depuis AND date_fin < &avant
 GROUP BY priorite
 ORDER BY DECODE(priorite, 'CRITIQUE', 1, 'HAUTE', 2, 'NORMALE', 3, 4);

PROMPT === Charge par technicien ===
SELECT u.prenom || ' ' || u.nom AS technicien,
       COUNT(CASE WHEN i.statut IN ('PLANIFIEE', 'EN_COURS') THEN 1 END) AS ouvertes,
       COUNT(CASE WHEN i.statut = 'TERMINEE' AND i.date_fin >= &depuis AND i.date_fin < &avant THEN 1 END) AS terminees,
       NVL(SUM(CASE WHEN i.statut = 'TERMINEE' AND i.date_fin >= &depuis AND i.date_fin < &avant
                    THEN i.temps_passe_min END), 0) AS minutes
  FROM utilisateur u
  LEFT JOIN intervention i ON i.id_technicien = u.id_utilisateur
 WHERE u.role = 'TECHNICIEN' AND u.actif = TRUE
 GROUP BY u.id_utilisateur, u.prenom, u.nom
 ORDER BY ouvertes DESC, terminees DESC, u.nom;