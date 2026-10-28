-- =====================================================================
-- MécaDétect - restaure les 5 index de performance, dans leur définition
-- finale (V5 puis V6), après la campagne Gatling « avant ».
-- =====================================================================
CREATE INDEX idx_historique_intervention
    ON historique_statut (id_intervention, date_changement);
CREATE INDEX idx_intervention_date_demande
    ON intervention (date_demande, id_intervention);
CREATE INDEX idx_intervention_statut_date
    ON intervention (statut, date_demande);
CREATE INDEX idx_intervention_equipement_date
    ON intervention (id_equipement, date_demande, id_intervention);
CREATE INDEX idx_intervention_technicien_statut
    ON intervention (id_technicien, statut);

PROMPT === Index sur intervention et historique_statut (6 + 2 attendus)
SELECT table_name, index_name FROM user_indexes
 WHERE table_name IN ('INTERVENTION', 'HISTORIQUE_STATUT') ORDER BY table_name, index_name;
EXIT