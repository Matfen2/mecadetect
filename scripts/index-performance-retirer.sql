-- =====================================================================
-- MécaDétect - campagne Gatling « avant » (tâche 4.8)
--
-- Retire TEMPORAIREMENT les 5 index de performance de V5/V6, pour mesurer
-- l'API dans l'état d'avant l'optimisation. Flyway ne vérifie pas les index :
-- l'API continue de démarrer normalement.
--
-- ⚠ Toujours exécuter index-performance-restaurer.sql après la campagne.
-- =====================================================================
DROP INDEX idx_historique_intervention;
DROP INDEX idx_intervention_date_demande;
DROP INDEX idx_intervention_statut_date;
DROP INDEX idx_intervention_equipement_date;
DROP INDEX idx_intervention_technicien_statut;

PROMPT === Index restants sur intervention et historique_statut
SELECT table_name, index_name FROM user_indexes
 WHERE table_name IN ('INTERVENTION', 'HISTORIQUE_STATUT') ORDER BY table_name, index_name;
EXIT