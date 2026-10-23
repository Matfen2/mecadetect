-- =====================================================================
-- MécaDétect - V3 : historisation automatique des statuts (UC-DB1, RG-08)
--
-- Chaque changement de statut d'une intervention crée une ligne dans
-- HISTORIQUE_STATUT, quelle que soit son origine : l'API, un script SQL,
-- une procédure PL/SQL. La garantie est portée par la base, pas par le code.
--
-- - À la création : une ligne « (aucun) → DEMANDEE ».
-- - À chaque modification du statut : une ligne « ancien → nouveau ».
-- - Une mise à jour qui ne change pas le statut n'écrit rien (clause WHEN).
-- - L'auteur est la personne qui a fait la modification (id_modifie_par),
--   tenue à jour par l'application à chaque transition.
-- - La date est l'instant de l'enregistrement, en UTC comme toutes les
--   dates de l'application.
-- =====================================================================
CREATE OR REPLACE TRIGGER trg_intervention_historique
    AFTER INSERT OR UPDATE OF statut ON intervention
    FOR EACH ROW
    WHEN (OLD.statut IS NULL OR NEW.statut <> OLD.statut)
BEGIN
    INSERT INTO historique_statut (ancien_statut, nouveau_statut, date_changement, id_intervention, id_auteur)
    VALUES (:OLD.statut, :NEW.statut, SYS_EXTRACT_UTC(SYSTIMESTAMP), :NEW.id_intervention, :NEW.id_modifie_par);
END;
/