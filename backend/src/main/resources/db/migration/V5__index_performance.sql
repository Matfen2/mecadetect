-- =====================================================================
-- MécaDétect - V5 : index des requêtes critiques (tâche 4.4, UC-DB3)
--
-- Chaque index répond à un plan d'exécution mesuré sur 500 000 interventions
-- et 1,9 million de lignes d'historique (scripts/mesures-performance.sql,
-- résultats dans docs/PERFORMANCE.md). Avant cette migration, les seuls index
-- étaient ceux des clés primaires et des contraintes d'unicité : chacune des
-- requêtes ci-dessous parcourait toute la table (TABLE ACCESS FULL).
--
-- Coût assumé : chaque insertion d'intervention met à jour 5 index de plus.
-- Dans une GMAO, on lit beaucoup plus qu'on n'écrit (quelques déclarations
-- par heure, des centaines de consultations).
-- =====================================================================

-- Détail d'une intervention : son historique (4 lignes sur 1,9 million).
-- Indexe aussi la clé étrangère : sans index, supprimer une intervention
-- verrouillerait toute la table d'historique.
CREATE INDEX idx_historique_intervention
    ON historique_statut (id_intervention, date_changement);

-- Liste des interventions, triée par défaut sur la date de demande décroissante :
-- Oracle lit l'index à l'envers et s'arrête après 20 lignes (COUNT STOPKEY).
CREATE INDEX idx_intervention_date_demande
    ON intervention (date_demande);

-- Liste filtrée sur un statut, triée par date (ex. les 2 229 planifiées).
CREATE INDEX idx_intervention_statut_date
    ON intervention (statut, date_demande);

-- Fiche équipement : son historique, trié par date. Indexe aussi la clé étrangère.
CREATE INDEX idx_intervention_equipement_date
    ON intervention (id_equipement, date_demande);

-- « Mes interventions », RG-04 (une seule en cours) et filtre technicien.
-- Le statut en deuxième position permet de ne lire que les interventions ouvertes.
CREATE INDEX idx_intervention_technicien_statut
    ON intervention (id_technicien, statut);