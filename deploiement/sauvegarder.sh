#!/usr/bin/env bash
# Sauvegarde de la base de MécaDétect (tâche 5.8).
#
# Export Data Pump du schéma de l'application, cohérent à un instant donné,
# compressé, avec son empreinte SHA-256, puis :
#   - conservé 7 jours sur la VM (dossier sauvegardes/) ;
#   - copié dans le bucket Object Storage, où une règle le supprime après 30 jours.
#
# La VM s'authentifie auprès d'Oracle Cloud par principal d'instance : aucune clé
# sur le serveur. Elle peut déposer et relire, mais ni écraser ni supprimer.
#
# Lancé chaque nuit par cron (02:30, avant la réinitialisation de la démonstration),
# ou à la main : /opt/mecadetect/sauvegarder.sh

set -euo pipefail
umask 077 # sauvegardes et journaux lisibles par ubuntu seulement

DOSSIER=/opt/mecadetect
LOCAL=$DOSSIER/sauvegardes
CONSERVATION_JOURS=${SAUVEGARDE_JOURS:-7}
# Sur le volume des données d'Oracle : le système de fichiers du conteneur reste propre
DANS_CONTENEUR=/opt/oracle/oradata/sauvegardes
OCI=${OCI:-/home/ubuntu/.local/bin/oci}
export OCI_CLI_AUTH=instance_principal OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True

journal() { printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$1"; }

cd "$DOSSIER"
COMPOSE=(docker compose -f compose.prod.yaml --env-file .env.prod)

set -a
# shellcheck source=/dev/null
. ./.env.prod
set +a
NAMESPACE=${SAUVEGARDE_NAMESPACE:?SAUVEGARDE_NAMESPACE manquant dans .env.prod}
BUCKET=${SAUVEGARDE_BUCKET:-mecadetect-sauvegardes}
SCHEMA=${ORACLE_APP_USER^^}

# Une seule opération à la fois sur la base : sauvegarde, restauration, réinitialisation
exec 9>"$DOSSIER/.verrou-base"
flock -w 1800 9 || { journal "Base occupée depuis 30 minutes : sauvegarde abandonnée."; exit 1; }

nom=mecadetect-$(date '+%Y%m%d-%H%M%S')

# sqlplus dans le conteneur, connexion lue sur l'entrée standard :
# le mot de passe n'apparaît pas dans la liste des processus
sqlplus_system() { "${COMPOSE[@]}" exec -T oracle sqlplus -s -L /nolog; }
dans_oracle() { "${COMPOSE[@]}" exec -T oracle "$@"; }

# Fichiers de travail retirés du conteneur dans tous les cas
nettoyer() { dans_oracle rm -f "$DANS_CONTENEUR/$nom.dmp" "$DANS_CONTENEUR/$nom.log" || true; }
trap nettoyer EXIT

journal "Début de la sauvegarde $nom (schéma $SCHEMA)"
install -d -m 700 "$LOCAL"
dans_oracle mkdir -p "$DANS_CONTENEUR"

journal "Export Data Pump"
sqlplus_system <<SQL
WHENEVER SQLERROR EXIT FAILURE
CONNECT system/"${ORACLE_PASSWORD}"@//localhost/FREEPDB1
SET FEEDBACK OFF
CREATE OR REPLACE DIRECTORY sauvegarde_dir AS '${DANS_CONTENEUR}';
DECLARE
    h    NUMBER;
    etat VARCHAR2(30);
BEGIN
    h := DBMS_DATAPUMP.OPEN(operation => 'EXPORT', job_mode => 'SCHEMA');
    DBMS_DATAPUMP.ADD_FILE(h, '${nom}.dmp', 'SAUVEGARDE_DIR');
    DBMS_DATAPUMP.ADD_FILE(h, '${nom}.log', 'SAUVEGARDE_DIR',
                           filetype => DBMS_DATAPUMP.KU\$_FILE_TYPE_LOG_FILE);
    DBMS_DATAPUMP.METADATA_FILTER(h, 'SCHEMA_LIST', '''${SCHEMA}''');
    -- Image cohérente : toutes les tables telles qu'au début de l'export
    DBMS_DATAPUMP.SET_PARAMETER(h, 'FLASHBACK_TIME', 'SYSTIMESTAMP');
    DBMS_DATAPUMP.START_JOB(h);
    DBMS_DATAPUMP.WAIT_FOR_JOB(h, etat);
    IF etat <> 'COMPLETED' THEN
        RAISE_APPLICATION_ERROR(-20901, 'Export Data Pump : ' || etat);
    END IF;
END;
/
EXIT
SQL

# Rapatriement par l'entrée/sortie standard : fichiers créés au nom d'ubuntu
dans_oracle cat "$DANS_CONTENEUR/$nom.log" > "$LOCAL/$nom.log"
if ! grep -q 'successfully completed' "$LOCAL/$nom.log"; then
    journal "Export incomplet, voir $LOCAL/$nom.log"
    exit 1
fi
dans_oracle cat "$DANS_CONTENEUR/$nom.dmp" | gzip -6 > "$LOCAL/$nom.dmp.gz"
(cd "$LOCAL" && sha256sum "$nom.dmp.gz" > "$nom.dmp.gz.sha256")
journal "Export terminé : $(du -h "$LOCAL/$nom.dmp.gz" | cut -f1) compressés"

journal "Copie vers le bucket $BUCKET"
for fichier in "$nom.dmp.gz" "$nom.dmp.gz.sha256" "$nom.log"; do
    "$OCI" os object put --namespace "$NAMESPACE" --bucket-name "$BUCKET" \
        --file "$LOCAL/$fichier" --name "$fichier" --no-overwrite >/dev/null
done

journal "Rotation : suppression des sauvegardes locales de plus de $CONSERVATION_JOURS jours"
find "$LOCAL" -maxdepth 1 -type f -name 'mecadetect-*' -mtime "+$((CONSERVATION_JOURS - 1))" -print -delete

journal "Sauvegarde terminée : $nom"