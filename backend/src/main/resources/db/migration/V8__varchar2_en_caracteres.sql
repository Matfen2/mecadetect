-- =====================================================================
-- MécaDétect - V8 : longueurs des VARCHAR2 exprimées en caractères
--
-- Par défaut (NLS_LENGTH_SEMANTICS = BYTE), VARCHAR2(100) accepte 100 OCTETS.
-- En AL32UTF8, un caractère accentué en occupe 2 ou plus : « é » compte double,
-- et un libellé de 100 caractères accentués était refusé (ORA-12899).
-- Toutes les colonnes VARCHAR2 passent donc en CHAR, y compris les codes
-- en ASCII (statuts, rôles, priorités) : une seule règle pour tout le schéma.
--
-- - V1 et V7 ne sont pas modifiées : Flyway vérifie leur somme de contrôle.
-- - Augmenter la capacité d'une colonne ne touche que le dictionnaire :
--   ni réécriture des données ni reconstruction des index, même sur
--   des centaines de milliers d'interventions.
-- - MODIFY conserve NOT NULL, DEFAULT et les contraintes existantes.
-- - Les tables d'archive suivent les tables sources : sinon l'INSERT ... SELECT
--   de PKG_MAINTENANCE échouerait sur une description accentuée.
-- - Limite Oracle inchangée (MAX_STRING_SIZE = STANDARD) : 4 000 octets par colonne.
-- =====================================================================

ALTER TABLE zone MODIFY (
    code     VARCHAR2(10 CHAR),
    libelle  VARCHAR2(100 CHAR)
);

ALTER TABLE type_equipement MODIFY (
    libelle  VARCHAR2(100 CHAR)
);

ALTER TABLE utilisateur MODIFY (
    nom                VARCHAR2(50 CHAR),
    prenom             VARCHAR2(50 CHAR),
    email              VARCHAR2(255 CHAR),
    mot_de_passe_hash  VARCHAR2(100 CHAR),
    role               VARCHAR2(20 CHAR)
);

ALTER TABLE equipement MODIFY (
    reference  VARCHAR2(30 CHAR),
    nom        VARCHAR2(100 CHAR),
    statut     VARCHAR2(20 CHAR)
);

ALTER TABLE intervention MODIFY (
    numero            VARCHAR2(20 CHAR),
    titre             VARCHAR2(100 CHAR),
    description       VARCHAR2(1000 CHAR),
    priorite          VARCHAR2(10 CHAR),
    statut            VARCHAR2(20 CHAR),
    compte_rendu      VARCHAR2(2000 CHAR),
    motif_annulation  VARCHAR2(500 CHAR)
);

ALTER TABLE historique_statut MODIFY (
    ancien_statut   VARCHAR2(20 CHAR),
    nouveau_statut  VARCHAR2(20 CHAR)
);

ALTER TABLE intervention_archive MODIFY (
    numero            VARCHAR2(20 CHAR),
    titre             VARCHAR2(100 CHAR),
    description       VARCHAR2(1000 CHAR),
    priorite          VARCHAR2(10 CHAR),
    statut            VARCHAR2(20 CHAR),
    compte_rendu      VARCHAR2(2000 CHAR),
    motif_annulation  VARCHAR2(500 CHAR)
);

ALTER TABLE historique_statut_archive MODIFY (
    ancien_statut   VARCHAR2(20 CHAR),
    nouveau_statut  VARCHAR2(20 CHAR)
);

ALTER TABLE journal_archivage MODIFY (
    statut   VARCHAR2(10 CHAR),
    message  VARCHAR2(4000 CHAR)
);

-- Le trigger de V3 et PKG_MAINTENANCE dépendent des tables modifiées : Oracle les a invalidés.
-- Les recompiler ici fait apparaître une éventuelle erreur pendant la migration,
-- et non au premier archivage en production.
BEGIN
    DBMS_UTILITY.COMPILE_SCHEMA(schema => USER, compile_all => FALSE);
END;
/