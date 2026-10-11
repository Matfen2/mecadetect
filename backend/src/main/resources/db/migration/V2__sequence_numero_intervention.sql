-- =====================================================================
-- MécaDétect - V2 : séquence des numéros d'intervention
-- Le numéro métier (INT-2026-000123) est distinct de la clé technique :
-- il est lisible par les utilisateurs et ne révèle pas l'ordre d'insertion
-- des autres tables. La séquence garantit son unicité sans verrou applicatif.
-- =====================================================================
CREATE SEQUENCE seq_numero_intervention START WITH 1 INCREMENT BY 1 CACHE 20;