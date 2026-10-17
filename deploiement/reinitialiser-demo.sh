#!/usr/bin/env bash
# Remet le site de démonstration à neuf : schéma vidé, migrations Flyway rejouées,
# comptes et référentiel de démonstration recréés, interventions régénérées.
# Lancé chaque nuit par cron (03:00), ou à la main après le premier déploiement :
#   /opt/mecadetect/reinitialiser-demo.sh
# Refuse de s'exécuter si le profil demo n'est pas actif : jamais sur une vraie production.

set -euo pipefail

DOSSIER=/opt/mecadetect
INTERVENTIONS=${INTERVENTIONS_DEMO:-200000}
ANNEES=${ANNEES_DEMO:-3}

journal() { printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$1"; }

cd "$DOSSIER"
COMPOSE=(docker compose -f compose.prod.yaml --env-file .env.prod)

# Variables du déploiement (mots de passe Oracle, profils)
set -a
# shellcheck source=/dev/null
. ./.env.prod
set +a

if [[ ",${PROFILS:-prod}," != *,demo,* ]]; then
    journal "Profil demo inactif (PROFILS=${PROFILS:-prod}) : réinitialisation refusée."
    exit 1
fi

# Une seule opération à la fois sur la base : sauvegarde, restauration, réinitialisation
exec 9>"$DOSSIER/.verrou-base"
flock -w 1800 9 || { journal "Base occupée depuis 30 minutes : réinitialisation abandonnée."; exit 1; }

# sqlplus dans le conteneur Oracle ; la connexion est lue sur l'entrée standard,
# le mot de passe n'apparaît donc pas dans la liste des processus
sqlplus_application() {
    "${COMPOSE[@]}" exec -T oracle sqlplus -s -L /nolog
}

journal "Début de la réinitialisation ($INTERVENTIONS interventions sur $ANNEES ans)"

journal "Arrêt de l'API"
"${COMPOSE[@]}" stop api

journal "Suppression des objets du schéma $ORACLE_APP_USER"
# L'utilisateur et ses droits sont conservés : seuls ses objets sont supprimés.
# Deux passes, car certains objets dépendent d'autres (vues, types).
sqlplus_application <<SQL
WHENEVER SQLERROR EXIT FAILURE
CONNECT ${ORACLE_APP_USER}/"${ORACLE_APP_PASSWORD}"@//localhost/FREEPDB1
SET SERVEROUTPUT ON FEEDBACK OFF
DECLARE
    v_restants PLS_INTEGER;
BEGIN
    FOR passe IN 1 .. 2 LOOP
        FOR o IN (SELECT object_name, object_type
                    FROM user_objects
                   WHERE object_type IN ('MATERIALIZED VIEW', 'VIEW', 'TABLE', 'SEQUENCE',
                                         'PACKAGE', 'PROCEDURE', 'FUNCTION', 'TYPE', 'SYNONYM')
                     AND object_name NOT LIKE 'ISEQ\$\$%'
                     AND object_name NOT LIKE 'BIN\$%') LOOP
            BEGIN
                EXECUTE IMMEDIATE 'DROP ' || o.object_type || ' "' || o.object_name || '"'
                    || CASE o.object_type
                           WHEN 'TABLE' THEN ' CASCADE CONSTRAINTS PURGE'
                           WHEN 'TYPE' THEN ' FORCE'
                       END;
            EXCEPTION
                WHEN OTHERS THEN NULL;  -- déjà supprimé avec un autre objet : on continue
            END;
        END LOOP;
    END LOOP;
    EXECUTE IMMEDIATE 'PURGE RECYCLEBIN';
    SELECT COUNT(*) INTO v_restants FROM user_objects WHERE object_type IN ('TABLE', 'VIEW', 'PACKAGE');
    IF v_restants > 0 THEN
        RAISE_APPLICATION_ERROR(-20900, v_restants || ' objet(s) non supprimé(s)');
    END IF;
END;
/
EXIT
SQL

journal "Redémarrage de l'API : migrations Flyway et données de démonstration"
"${COMPOSE[@]}" up -d --no-build --wait --wait-timeout 300 api

journal "Génération des interventions"
sqlplus_application <<SQL
WHENEVER SQLERROR EXIT FAILURE
CONNECT ${ORACLE_APP_USER}/"${ORACLE_APP_PASSWORD}"@//localhost/FREEPDB1
SET FEEDBACK OFF TIMING ON
EXEC pkg_generation.generer_interventions(p_nombre => ${INTERVENTIONS}, p_annees => ${ANNEES});
SELECT COUNT(*) AS interventions FROM intervention;
EXIT
SQL

journal "Réinitialisation terminée"