-- =====================================================================
-- MécaDétect - V6 : index adaptés au tri de la pagination (tâche 4.4)
--
-- Constat des mesures après la V5 (docs/PERFORMANCE.md) : la liste sans filtre
-- et l'historique d'un équipement parcouraient encore toute la table. Le tri
-- « date_demande DESC, id_intervention ASC » mélangeait deux sens : aucun index
-- ne pouvait fournir cet ordre, Oracle triait donc les 500 000 lignes pour en
-- afficher 20.
--
-- Correction en deux parties :
-- - côté Java, l'identifiant départage désormais dans le même sens que le tri
--   (PaginationIntervention) : « date_demande DESC, id_intervention DESC » ;
-- - côté base, l'identifiant rejoint ces deux index : lus à l'envers, ils
--   fournissent exactement cet ordre, et Oracle s'arrête après 20 lignes.
-- =====================================================================

DROP INDEX idx_intervention_date_demande;
CREATE INDEX idx_intervention_date_demande
    ON intervention (date_demande, id_intervention);

DROP INDEX idx_intervention_equipement_date;
CREATE INDEX idx_intervention_equipement_date
    ON intervention (id_equipement, date_demande, id_intervention);