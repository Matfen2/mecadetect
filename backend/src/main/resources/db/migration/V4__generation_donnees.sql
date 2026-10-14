-- =====================================================================
-- MécaDétect - V4 : génération d'un historique réaliste (tâche 4.1)
--
-- PKG_GENERATION.GENERER_INTERVENTIONS crée en une commande un volume
-- d'interventions réparties sur plusieurs années (500 000 sur 5 ans par
-- défaut), avec leur historique de statuts, pour les mesures du Sprint 4 :
-- plans d'exécution, index, reporting et tests de charge.
--
--   EXEC pkg_generation.generer_interventions;                 -- 500 000 sur 5 ans
--   EXEC pkg_generation.generer_interventions(p_nombre => 50000);
--
-- Techniques :
-- - tableaux PL/SQL remplis par lots, insérés avec FORALL (un aller-retour
--   SQL par lot et par table, au lieu d'un par ligne) ;
-- - DBMS_RANDOM pour les dates, statuts, priorités, durées et textes ;
-- - RETURNING ... BULK COLLECT pour récupérer les identifiants créés.
--
-- Cohérence des données :
-- - toutes les contraintes CHECK de la V1 sont respectées ;
-- - un technicien n'intervient que sur un type où il est habilité (RG-09) ;
-- - aucune intervention EN_COURS n'est générée (RG-04 reste vrai) ;
-- - l'historique (3 à 4 lignes par intervention) suit les dates métier ;
-- - le statut des équipements est recalculé à la fin (RG-05).
--
-- Le trigger d'historique (V3) est neutralisé pendant la génération, pour la
-- session qui génère uniquement : le générateur écrit lui-même un historique
-- daté, alors que le trigger n'aurait vu qu'une insertion au statut final.
-- =====================================================================

CREATE OR REPLACE PACKAGE pkg_generation AS

    -- Vrai pendant une génération, dans la session qui génère uniquement
    -- (l'état d'un package est propre à chaque session Oracle).
    g_generation_en_cours BOOLEAN := FALSE;

    -- p_nombre      : nombre d'interventions à créer
    -- p_annees      : profondeur de l'historique, en années
    -- p_lot         : taille des lots insérés avec FORALL
    -- p_techniciens : nombre minimal de techniciens actifs (des comptes sont créés si besoin)
    -- p_commit      : FALSE dans les tests, pour que tout soit annulé à la fin du test
    PROCEDURE generer_interventions(
        p_nombre      IN PLS_INTEGER DEFAULT 500000,
        p_annees      IN PLS_INTEGER DEFAULT 5,
        p_lot         IN PLS_INTEGER DEFAULT 10000,
        p_techniciens IN PLS_INTEGER DEFAULT 12,
        p_commit      IN BOOLEAN     DEFAULT TRUE
    );

END pkg_generation;
/

CREATE OR REPLACE PACKAGE BODY pkg_generation AS

    TYPE t_nombres IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    TYPE t_textes  IS TABLE OF VARCHAR2(2000) INDEX BY PLS_INTEGER;
    TYPE t_dates   IS TABLE OF TIMESTAMP INDEX BY PLS_INTEGER;
    TYPE t_liste   IS TABLE OF NUMBER;
    TYPE t_techniciens_par_type IS TABLE OF t_liste INDEX BY PLS_INTEGER;

    c_jours_ouverts CONSTANT PLS_INTEGER := 30;  -- seules les demandes récentes restent ouvertes

    -- ------------------------------------------------------------------ outils
    FUNCTION hasard(p_bornes IN PLS_INTEGER) RETURN PLS_INTEGER IS
    BEGIN
        RETURN TRUNC(DBMS_RANDOM.VALUE(1, p_bornes + 1));
    END;

    FUNCTION heures(p_nombre IN NUMBER) RETURN INTERVAL DAY TO SECOND IS
    BEGIN
        RETURN NUMTODSINTERVAL(p_nombre, 'HOUR');
    END;

    -- Crée des techniciens si l'atelier en compte moins que demandé, puis garantit
    -- que chaque type d'équipement a au moins un technicien habilité (RG-09).
    -- Les comptes créés ne peuvent pas se connecter : leur empreinte n'est pas un BCrypt.
    PROCEDURE completer_techniciens(p_minimum IN PLS_INTEGER) IS
        v_prenoms SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
            'Hugo', 'Inès', 'Karim', 'Léa', 'Mehdi', 'Nadia', 'Paul', 'Sarah', 'Thomas', 'Yasmine', 'Antoine', 'Camille');
        v_noms SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
            'Petit', 'Roux', 'Fontaine', 'Garnier', 'Lambert', 'Morel', 'Girard', 'André', 'Mercier', 'Blanc', 'Guérin', 'Faure');
        v_types     t_liste;
        v_actifs    PLS_INTEGER;
        v_id        NUMBER;
        v_email     VARCHAR2(255);
        v_existe    PLS_INTEGER;
        v_rang      PLS_INTEGER := 0;
        v_tous      t_liste;
        -- Valeurs calculées en PL/SQL avant chaque ordre SQL : une méthode de collection
        -- comme COUNT n'est pas utilisable à l'intérieur d'une instruction SQL.
        v_nom       VARCHAR2(50);
        v_prenom    VARCHAR2(50);
        v_id_type   NUMBER;
        v_id_tech   NUMBER;
    BEGIN
        SELECT id_type BULK COLLECT INTO v_types FROM type_equipement ORDER BY id_type;
        IF v_types.COUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20001, 'Aucun type d''équipement : rien à générer.');
        END IF;

        SELECT COUNT(*) INTO v_actifs FROM utilisateur WHERE role = 'TECHNICIEN' AND actif = TRUE;

        FOR i IN v_actifs + 1 .. p_minimum LOOP
            v_email := 'technicien.genere' || LPAD(i, 2, '0') || '@mecadetect.fr';
            SELECT COUNT(*) INTO v_existe FROM utilisateur WHERE email = v_email;
            IF v_existe = 0 THEN
                v_nom := v_noms(MOD(i - 1, v_noms.COUNT) + 1);
                v_prenom := v_prenoms(MOD(i - 1, v_prenoms.COUNT) + 1);
                INSERT INTO utilisateur (nom, prenom, email, mot_de_passe_hash, role, actif)
                VALUES (v_nom, v_prenom, v_email, '!', 'TECHNICIEN', TRUE)
                RETURNING id_utilisateur INTO v_id;

                -- Trois habilitations distinctes, réparties en tourniquet sur les types
                FOR k IN 0 .. LEAST(2, v_types.COUNT - 1) LOOP
                    v_id_type := v_types(MOD(i - 1 + k, v_types.COUNT) + 1);
                    INSERT INTO habilitation (id_type, id_technicien, date_habilitation)
                    VALUES (v_id_type, v_id, DATE '2021-01-04');
                END LOOP;
            END IF;
        END LOOP;

        -- Les techniciens les plus récents (souvent les générés) d'abord : les habilitations
        -- des comptes de démonstration restent intactes autant que possible.
        SELECT id_utilisateur BULK COLLECT INTO v_tous
          FROM utilisateur WHERE role = 'TECHNICIEN' AND actif = TRUE
         ORDER BY id_utilisateur DESC;
        IF v_tous.COUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20002, 'Aucun technicien actif.');
        END IF;

        FOR t IN (SELECT ty.id_type
                    FROM type_equipement ty
                   WHERE NOT EXISTS (SELECT 1
                                       FROM habilitation h
                                       JOIN utilisateur u ON u.id_utilisateur = h.id_technicien
                                      WHERE h.id_type = ty.id_type
                                        AND u.role = 'TECHNICIEN' AND u.actif = TRUE)) LOOP
            v_id_tech := v_tous(MOD(v_rang, v_tous.COUNT) + 1);
            INSERT INTO habilitation (id_type, id_technicien, date_habilitation)
            VALUES (t.id_type, v_id_tech, DATE '2021-01-04');
            v_rang := v_rang + 1;
        END LOOP;
    END completer_techniciens;

    -- ------------------------------------------------------------------ génération
    PROCEDURE generer_interventions(
        p_nombre      IN PLS_INTEGER DEFAULT 500000,
        p_annees      IN PLS_INTEGER DEFAULT 5,
        p_lot         IN PLS_INTEGER DEFAULT 10000,
        p_techniciens IN PLS_INTEGER DEFAULT 12,
        p_commit      IN BOOLEAN     DEFAULT TRUE
    ) IS
        v_titres SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
            'Fuite d''huile', 'Bruit anormal', 'Surchauffe moteur', 'Arrêt intempestif', 'Défaut capteur',
            'Vibrations excessives', 'Panne électrique', 'Usure de courroie', 'Perte de pression', 'Erreur automate');
        v_comptes_rendus SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
            'Joint remplacé, essai sous pression concluant.',
            'Roulement changé, niveau de bruit revenu à la normale.',
            'Capteur nettoyé puis recalibré, défaut disparu.',
            'Courroie remplacée et tension réglée.',
            'Connecteur oxydé remplacé, alimentation rétablie.',
            'Programme automate rechargé, cycle de test validé.',
            'Filtre colmaté remplacé, température stabilisée.');
        v_motifs SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
            'Doublon d''une demande existante.', 'Panne résolue par l''opérateur.',
            'Équipement remplacé avant intervention.', 'Demande erronée.');

        -- Référentiel chargé une fois
        v_equipements      t_liste;
        v_types_equipement t_liste;
        v_demandeurs       t_liste;
        v_admins           t_liste;
        v_habilites        t_techniciens_par_type;
        v_liste            t_liste;

        -- Un lot d'interventions
        i_titre    t_textes;  i_description t_textes;  i_priorite t_textes;  i_statut t_textes;
        i_demande  t_dates;   i_planifiee   t_dates;   i_debut    t_dates;   i_fin    t_dates;
        i_temps    t_nombres; i_compte_rendu t_textes; i_motif    t_textes;
        i_equipement t_nombres; i_demandeur t_nombres; i_technicien t_nombres; i_modifie_par t_nombres;
        i_admin    t_nombres; i_planification t_dates;  i_annulation t_dates;
        v_ids      t_liste;

        -- L'historique du lot
        h_ancien t_textes; h_nouveau t_textes; h_date t_dates; h_intervention t_nombres; h_auteur t_nombres;
        n_histo  PLS_INTEGER;

        v_maintenant TIMESTAMP := SYS_EXTRACT_UTC(SYSTIMESTAMP);
        v_debut_traitement NUMBER := DBMS_UTILITY.GET_TIME;   -- en centièmes de seconde
        v_restant  PLS_INTEGER := p_nombre;
        v_taille   PLS_INTEGER;
        v_age      NUMBER;
        v_r        NUMBER;
        v_delai    NUMBER;
        v_idx      PLS_INTEGER;
        v_total_histo PLS_INTEGER := 0;

        PROCEDURE ajouter_histo(p_ancien VARCHAR2, p_nouveau VARCHAR2, p_date TIMESTAMP,
                                p_intervention NUMBER, p_auteur NUMBER) IS
        BEGIN
            n_histo := n_histo + 1;
            h_ancien(n_histo) := p_ancien;
            h_nouveau(n_histo) := p_nouveau;
            h_date(n_histo) := p_date;
            h_intervention(n_histo) := p_intervention;
            h_auteur(n_histo) := p_auteur;
        END;
    BEGIN
        IF p_nombre < 1 OR p_annees < 1 OR p_lot < 1 THEN
            RAISE_APPLICATION_ERROR(-20003, 'Paramètres invalides : nombre, années et lot doivent être positifs.');
        END IF;

        g_generation_en_cours := TRUE;

        completer_techniciens(p_techniciens);

        SELECT id_equipement, id_type BULK COLLECT INTO v_equipements, v_types_equipement
          FROM equipement WHERE statut <> 'ARCHIVE' ORDER BY id_equipement;
        SELECT id_utilisateur BULK COLLECT INTO v_demandeurs
          FROM utilisateur WHERE role = 'DEMANDEUR' AND actif = TRUE;
        SELECT id_utilisateur BULK COLLECT INTO v_admins
          FROM utilisateur WHERE role = 'ADMIN' AND actif = TRUE;
        IF v_equipements.COUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20004, 'Aucun équipement en service : rien à générer.');
        END IF;
        IF v_demandeurs.COUNT = 0 THEN v_demandeurs := v_admins; END IF;
        IF v_admins.COUNT = 0 THEN v_admins := v_demandeurs; END IF;
        IF v_demandeurs.COUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20005, 'Aucun demandeur ni administrateur actif.');
        END IF;

        FOR t IN (SELECT DISTINCT id_type FROM equipement WHERE statut <> 'ARCHIVE') LOOP
            SELECT h.id_technicien BULK COLLECT INTO v_liste
              FROM habilitation h JOIN utilisateur u ON u.id_utilisateur = h.id_technicien
             WHERE h.id_type = t.id_type AND u.role = 'TECHNICIEN' AND u.actif = TRUE;
            v_habilites(t.id_type) := v_liste;
        END LOOP;

        WHILE v_restant > 0 LOOP
            v_taille := LEAST(p_lot, v_restant);
            i_titre.DELETE; i_description.DELETE; i_priorite.DELETE; i_statut.DELETE;
            i_demande.DELETE; i_planifiee.DELETE; i_debut.DELETE; i_fin.DELETE; i_temps.DELETE;
            i_compte_rendu.DELETE; i_motif.DELETE; i_equipement.DELETE; i_demandeur.DELETE;
            i_technicien.DELETE; i_modifie_par.DELETE; i_admin.DELETE; i_planification.DELETE; i_annulation.DELETE;

            FOR i IN 1 .. v_taille LOOP
                -- Équipement : tirage déséquilibré, certains équipements tombent plus souvent en panne
                v_idx := LEAST(v_equipements.COUNT, TRUNC(v_equipements.COUNT * POWER(DBMS_RANDOM.VALUE, 2)) + 1);
                i_equipement(i) := v_equipements(v_idx);
                v_liste := v_habilites(v_types_equipement(v_idx));

                i_titre(i) := v_titres(hasard(v_titres.COUNT));
                i_description(i) := i_titre(i) || ' constaté par l''opérateur en cours de production.';

                v_r := DBMS_RANDOM.VALUE;
                i_priorite(i) := CASE WHEN v_r < 0.20 THEN 'BASSE' WHEN v_r < 0.65 THEN 'NORMALE'
                                      WHEN v_r < 0.90 THEN 'HAUTE' ELSE 'CRITIQUE' END;

                v_age := DBMS_RANDOM.VALUE(0, p_annees * 365);
                i_demande(i) := v_maintenant - NUMTODSINTERVAL(v_age, 'DAY');
                i_demandeur(i) := v_demandeurs(hasard(v_demandeurs.COUNT));
                i_admin(i) := v_admins(hasard(v_admins.COUNT));

                v_r := DBMS_RANDOM.VALUE;
                IF v_age > c_jours_ouverts THEN
                    i_statut(i) := CASE WHEN v_r < 0.90 THEN 'TERMINEE' ELSE 'ANNULEE' END;
                ELSE
                    i_statut(i) := CASE WHEN v_r < 0.20 THEN 'DEMANDEE' WHEN v_r < 0.45 THEN 'PLANIFIEE'
                                        WHEN v_r < 0.90 THEN 'TERMINEE' ELSE 'ANNULEE' END;
                END IF;

                -- Délai de planification selon la priorité, en heures
                v_delai := CASE i_priorite(i)
                               WHEN 'CRITIQUE' THEN DBMS_RANDOM.VALUE(0.5, 4)
                               WHEN 'HAUTE'    THEN DBMS_RANDOM.VALUE(2, 24)
                               WHEN 'NORMALE'  THEN DBMS_RANDOM.VALUE(8, 72)
                               ELSE DBMS_RANDOM.VALUE(24, 168) END;
                i_planification(i) := i_demande(i) + heures(v_delai / 4);   -- moment où l'admin planifie
                i_planifiee(i) := i_demande(i) + heures(v_delai);           -- date prévue de l'intervention
                i_debut(i) := i_planifiee(i) + NUMTODSINTERVAL(DBMS_RANDOM.VALUE(0, 60), 'MINUTE');
                i_temps(i) := ROUND(20 + 460 * POWER(DBMS_RANDOM.VALUE, 2));
                i_fin(i) := i_debut(i) + NUMTODSINTERVAL(i_temps(i), 'MINUTE');
                i_technicien(i) := v_liste(hasard(v_liste.COUNT));

                -- Une intervention dont la fin serait dans le futur n'est encore que planifiée
                IF i_statut(i) = 'TERMINEE' AND i_fin(i) > v_maintenant THEN
                    i_statut(i) := 'PLANIFIEE';
                END IF;
                IF i_statut(i) = 'PLANIFIEE' AND i_planification(i) > v_maintenant THEN
                    i_statut(i) := 'DEMANDEE';
                END IF;

                i_compte_rendu(i) := NULL;
                i_motif(i) := NULL;
                i_annulation(i) := NULL;
                CASE i_statut(i)
                    WHEN 'DEMANDEE' THEN
                        i_planifiee(i) := NULL; i_debut(i) := NULL; i_fin(i) := NULL; i_temps(i) := NULL;
                        i_technicien(i) := NULL; i_modifie_par(i) := i_demandeur(i);
                    WHEN 'PLANIFIEE' THEN
                        i_debut(i) := NULL; i_fin(i) := NULL; i_temps(i) := NULL;
                        i_modifie_par(i) := i_admin(i);
                    WHEN 'TERMINEE' THEN
                        i_compte_rendu(i) := v_comptes_rendus(hasard(v_comptes_rendus.COUNT));
                        i_modifie_par(i) := i_technicien(i);
                    ELSE -- ANNULEE, une fois sur deux après la planification
                        i_debut(i) := NULL; i_fin(i) := NULL; i_temps(i) := NULL;
                        i_motif(i) := v_motifs(hasard(v_motifs.COUNT));
                        i_modifie_par(i) := i_admin(i);
                        IF DBMS_RANDOM.VALUE < 0.5 OR i_planification(i) > v_maintenant THEN
                            i_planifiee(i) := NULL; i_technicien(i) := NULL; i_planification(i) := NULL;
                            i_annulation(i) := LEAST(i_demande(i) + heures(DBMS_RANDOM.VALUE(0.5, 24)), v_maintenant);
                        ELSE
                            i_annulation(i) := LEAST(i_planification(i) + heures(DBMS_RANDOM.VALUE(0.5, 24)), v_maintenant);
                        END IF;
                END CASE;
            END LOOP;

            -- Un seul ordre INSERT pour tout le lot
            FORALL i IN 1 .. v_taille
                INSERT INTO intervention (numero, titre, description, priorite, statut, date_demande,
                                          date_planifiee, date_debut, date_fin, temps_passe_min,
                                          compte_rendu, motif_annulation, version,
                                          id_equipement, id_demandeur, id_technicien, id_modifie_par)
                VALUES ('INT-' || TO_CHAR(i_demande(i), 'YYYY') || '-'
                            || LPAD(seq_numero_intervention.NEXTVAL,
                                    GREATEST(6, LENGTH(TO_CHAR(seq_numero_intervention.NEXTVAL))), '0'),
                        i_titre(i), i_description(i), i_priorite(i), i_statut(i), i_demande(i),
                        i_planifiee(i), i_debut(i), i_fin(i), i_temps(i),
                        i_compte_rendu(i), i_motif(i), 0,
                        i_equipement(i), i_demandeur(i), i_technicien(i), i_modifie_par(i))
                RETURNING id_intervention BULK COLLECT INTO v_ids;

            -- Historique daté selon le parcours de chaque intervention
            n_histo := 0;
            h_ancien.DELETE; h_nouveau.DELETE; h_date.DELETE; h_intervention.DELETE; h_auteur.DELETE;
            FOR i IN 1 .. v_taille LOOP
                ajouter_histo(NULL, 'DEMANDEE', i_demande(i), v_ids(i), i_demandeur(i));
                IF i_technicien(i) IS NOT NULL THEN
                    ajouter_histo('DEMANDEE', 'PLANIFIEE', i_planification(i), v_ids(i), i_admin(i));
                END IF;
                CASE i_statut(i)
                    WHEN 'TERMINEE' THEN
                        ajouter_histo('PLANIFIEE', 'EN_COURS', i_debut(i), v_ids(i), i_technicien(i));
                        ajouter_histo('EN_COURS', 'TERMINEE', i_fin(i), v_ids(i), i_technicien(i));
                    WHEN 'ANNULEE' THEN
                        ajouter_histo(CASE WHEN i_technicien(i) IS NULL THEN 'DEMANDEE' ELSE 'PLANIFIEE' END,
                                      'ANNULEE', i_annulation(i), v_ids(i), i_admin(i));
                    ELSE NULL;
                END CASE;
            END LOOP;

            FORALL j IN 1 .. n_histo
                INSERT INTO historique_statut (ancien_statut, nouveau_statut, date_changement, id_intervention, id_auteur)
                VALUES (h_ancien(j), h_nouveau(j), h_date(j), h_intervention(j), h_auteur(j));

            v_total_histo := v_total_histo + n_histo;
            v_restant := v_restant - v_taille;
            IF p_commit THEN
                COMMIT;   -- un commit par lot : la génération ne retient pas un undo de plusieurs Go
            END IF;
        END LOOP;

        -- RG-05 : le statut des équipements suit leurs interventions ouvertes
        UPDATE equipement e
           SET statut = CASE
                   WHEN EXISTS (SELECT 1 FROM intervention i
                                 WHERE i.id_equipement = e.id_equipement AND i.statut = 'EN_COURS') THEN 'EN_MAINTENANCE'
                   WHEN EXISTS (SELECT 1 FROM intervention i
                                 WHERE i.id_equipement = e.id_equipement AND i.statut IN ('DEMANDEE', 'PLANIFIEE')) THEN 'EN_PANNE'
                   ELSE 'EN_SERVICE' END
         WHERE e.statut <> 'ARCHIVE';

        g_generation_en_cours := FALSE;

        IF p_commit THEN
            COMMIT;
            -- Statistiques à jour : l'optimiseur doit connaître les nouveaux volumes (étape suivante)
            DBMS_STATS.GATHER_TABLE_STATS(USER, 'INTERVENTION');
            DBMS_STATS.GATHER_TABLE_STATS(USER, 'HISTORIQUE_STATUT');
        END IF;

        DBMS_OUTPUT.PUT_LINE(p_nombre || ' interventions et ' || v_total_histo || ' lignes d''historique générées en '
                             || ROUND((DBMS_UTILITY.GET_TIME - v_debut_traitement) / 100, 1) || ' s.');
    EXCEPTION
        WHEN OTHERS THEN
            g_generation_en_cours := FALSE;
            RAISE;
    END generer_interventions;

END pkg_generation;
/

-- Le trigger d'historique cède la main au générateur, dans sa session uniquement
CREATE OR REPLACE TRIGGER trg_intervention_historique
    AFTER INSERT OR UPDATE OF statut ON intervention
    FOR EACH ROW
    WHEN (OLD.statut IS NULL OR NEW.statut <> OLD.statut)
BEGIN
    IF pkg_generation.g_generation_en_cours THEN
        RETURN;
    END IF;
    INSERT INTO historique_statut (ancien_statut, nouveau_statut, date_changement, id_intervention, id_auteur)
    VALUES (:OLD.statut, :NEW.statut, SYS_EXTRACT_UTC(SYSTIMESTAMP), :NEW.id_intervention, :NEW.id_modifie_par);
END;
/