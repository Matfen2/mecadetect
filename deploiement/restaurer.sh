#!/usr/bin/env bash
# Restauration d'une sauvegarde Data Pump de MécaDétect (tâche 5.8).
#
#   restaurer.sh --test [SAUVEGARDE]
#       Restaure dans un schéma temporaire, compare le nombre de lignes de chaque
#       table à celui de l'export, puis supprime ce schéma. Sans effet sur le site.
#       Par défaut : la sauvegarde la plus récente. Lancé chaque dimanche par cron.
#
#   restaurer.sh --remplacer SAUVEGARDE [--oui]
#       Remplace le schéma de l'application par la sauvegarde. L'API est arrêtée
#       pendant l'opération. Confirmation demandée, sauf avec --oui.
#
# SAUVEGARDE : un fichier mecadetect-AAAAMMJJ-HHMMSS.dmp.gz. S'il n'est pas dans
# sauvegardes/ (VM reconstruite, sauvegarde de plus de 7 jours), il est téléchargé
# depuis le bucket. Son empreinte SHA-256 est contrôlée avant toute restauration.

set -euo pipefail
umask 077

DOSSIER=/opt/mecadetect
LOCAL=$DOSSIER/sauvegardes
DANS_CONTENEUR=/opt/oracle/oradata/sauvegardes
OCI=${OCI:-/home/ubuntu/.local/bin/oci}
export OCI_CLI_AUTH=instance_principal OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True

journal() { printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$1"; }
usage() { sed -n '4,16p' "$0" | sed -E 's/^# ?//' >&2; exit 2; }

# ------------------------------------------------------------------ Arguments
mode=${1:-}
[[ $mode == --test || $mode == --remplacer ]] || usage
shift
fichier=""
confirme=false
for argument in "$@"; do
    case $argument in
        --oui) confirme=true ;;
        -*) usage ;;
        *) fichier=$argument ;;
    esac
done
if [[ $mode == --remplacer && -z $fichier ]]; then usage; fi

cd "$DOSSIER"
COMPOSE=(docker compose -f compose.prod.yaml --env-file .env.prod)

set -a
# shellcheck source=/dev/null
. ./.env.prod
set +a
NAMESPACE=${SAUVEGARDE_NAMESPACE:?SAUVEGARDE_NAMESPACE manquant dans .env.prod}
BUCKET=${SAUVEGARDE_BUCKET:-mecadetect-sauvegardes}
SCHEMA=${ORACLE_APP_USER^^}
SCHEMA_TEST=${SCHEMA}_RESTAURATION

exec 9>"$DOSSIER/.verrou-base"
flock -w 1800 9 || { journal "Base occupée depuis 30 minutes : restauration abandonnée."; exit 1; }

sqlplus_system() { "${COMPOSE[@]}" exec -T oracle sqlplus -s -L /nolog; }
dans_oracle() { "${COMPOSE[@]}" exec -T oracle "$@"; }
connexion() { printf 'WHENEVER SQLERROR EXIT FAILURE\nCONNECT system/"%s"@//localhost/FREEPDB1\n' "$ORACLE_PASSWORD"; }

supprimer_schema() {
    { connexion; cat <<SQL
BEGIN
    EXECUTE IMMEDIATE 'DROP USER "$1" CASCADE';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1918 THEN RAISE; END IF; -- ORA-01918 : utilisateur inexistant
END;
/
EXIT
SQL
    } | sqlplus_system
}

# ------------------------------------------------------------------ Sauvegarde à restaurer
install -d -m 700 "$LOCAL"
if [[ -z $fichier ]]; then
    fichier=$(find "$LOCAL" -maxdepth 1 -name 'mecadetect-*.dmp.gz' -printf '%f\n' | sort | tail -n 1)
    [[ -n $fichier ]] || { journal "Aucune sauvegarde dans $LOCAL"; exit 1; }
fi
fichier=$(basename "$fichier")
if [[ ! $fichier =~ ^mecadetect-[0-9]{8}-[0-9]{6}\.dmp\.gz$ ]]; then
    journal "Nom de sauvegarde inattendu : $fichier"
    exit 2
fi
nom=${fichier%.dmp.gz}

for objet in "$fichier" "$fichier.sha256" "$nom.log"; do
    if [[ ! -f $LOCAL/$objet ]]; then
        journal "Téléchargement de $objet depuis le bucket $BUCKET"
        "$OCI" os object get --namespace "$NAMESPACE" --bucket-name "$BUCKET" \
            --name "$objet" --file "$LOCAL/$objet" >/dev/null
    fi
done

if ! (cd "$LOCAL" && sha256sum --quiet -c "$fichier.sha256"); then
    journal "Empreinte SHA-256 incorrecte : $fichier est altéré, restauration refusée."
    exit 1
fi

# Lignes exportées par table, d'après le journal de l'export :
#   . . exported "MECADETECT"."INTERVENTION"     6.4 MB  200000 rows
# (une ligne par partition pour une table partitionnée : on additionne)
attendu=$(awk -v schema="$SCHEMA" '
    $1 == "." && $2 == "." && $3 == "exported" && $NF == "rows" {
        objet = $4; sub(/:.*/, "", objet); gsub(/"/, "", objet)
        split(objet, partie, ".")
        if (partie[1] != schema) next
        lignes = $(NF - 1); gsub(/,/, "", lignes)
        total[partie[2]] += lignes
    }
    END { for (table in total) print table, total[table] }' "$LOCAL/$nom.log" | sort)
if [[ -z $attendu ]]; then
    journal "Journal d'export illisible ($LOCAL/$nom.log) : impossible de contrôler la restauration."
    exit 1
fi

# Nombre de lignes réel de chaque table attendue, dans le schéma $1
compter() {
    { connexion
      echo 'SET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 200'
      while read -r table _; do
          echo "SELECT '$table ' || COUNT(*) FROM \"$1\".\"$table\";"
      done <<< "$attendu"
      echo 'EXIT'
    } | sqlplus_system | awk 'NF == 2' | sort
}

# Import Data Pump de la sauvegarde ; $1 : schéma cible
importer() {
    local remappage=""
    if [[ $1 != "$SCHEMA" ]]; then
        remappage="DBMS_DATAPUMP.METADATA_REMAP(h, 'REMAP_SCHEMA', '$SCHEMA', '$1');"
    fi
    { connexion; cat <<SQL
SET FEEDBACK OFF
CREATE OR REPLACE DIRECTORY sauvegarde_dir AS '${DANS_CONTENEUR}';
DECLARE
    h    NUMBER;
    etat VARCHAR2(30);
BEGIN
    h := DBMS_DATAPUMP.OPEN(operation => 'IMPORT', job_mode => 'SCHEMA');
    DBMS_DATAPUMP.ADD_FILE(h, '${nom}.dmp', 'SAUVEGARDE_DIR');
    DBMS_DATAPUMP.ADD_FILE(h, '${nom}-import.log', 'SAUVEGARDE_DIR',
                           filetype => DBMS_DATAPUMP.KU\$_FILE_TYPE_LOG_FILE);
    ${remappage}
    DBMS_DATAPUMP.START_JOB(h);
    DBMS_DATAPUMP.WAIT_FOR_JOB(h, etat);
    IF etat <> 'COMPLETED' THEN
        RAISE_APPLICATION_ERROR(-20902, 'Import Data Pump : ' || etat);
    END IF;
END;
/
EXIT
SQL
    } | sqlplus_system
}

controler() {
    local obtenu
    obtenu=$(compter "$1")
    if [[ $obtenu != "$attendu" ]]; then
        journal "Écart entre l'export et la restauration (< attendu, > obtenu) :"
        diff <(echo "$attendu") <(echo "$obtenu") || true
        return 1
    fi
    journal "Contrôle réussi : $(wc -l <<< "$attendu") tables, $(awk '{ s += $2 } END { print s }' <<< "$attendu") lignes, identiques à l'export"
}

nettoyer() {
    dans_oracle rm -f "$DANS_CONTENEUR/$nom.dmp" "$DANS_CONTENEUR/$nom-import.log" || true
    if [[ $mode == --test ]]; then supprimer_schema "$SCHEMA_TEST" >/dev/null || true; fi
}
trap nettoyer EXIT

# ------------------------------------------------------------------ Restauration
if [[ $mode == --remplacer && $confirme != true ]]; then
    echo "Le schéma $SCHEMA va être REMPLACÉ par $fichier : les données actuelles seront perdues."
    read -r -p "Pour confirmer, tape le nom du schéma ($SCHEMA) : " reponse
    [[ $reponse == "$SCHEMA" ]] || { journal "Restauration annulée."; exit 1; }
fi

journal "Restauration de $fichier ($mode)"
dans_oracle mkdir -p "$DANS_CONTENEUR"
# Envoi par l'entrée standard : fichier créé au nom de l'utilisateur oracle du conteneur
gunzip -c "$LOCAL/$fichier" | dans_oracle bash -c "cat > '$DANS_CONTENEUR/$nom.dmp'"

if [[ $mode == --test ]]; then
    supprimer_schema "$SCHEMA_TEST"
    journal "Import dans le schéma temporaire $SCHEMA_TEST"
    importer "$SCHEMA_TEST"
    controler "$SCHEMA_TEST"
    printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$fichier" > "$LOCAL/.dernier-test-restauration"
    journal "Test de restauration réussi (le schéma temporaire est supprimé en sortie)"
    exit 0
fi

journal "Arrêt de l'API"
"${COMPOSE[@]}" stop api
journal "Suppression du schéma $SCHEMA"
supprimer_schema "$SCHEMA"
journal "Import : utilisateur, droits et objets recréés depuis la sauvegarde"
importer "$SCHEMA"
controler "$SCHEMA"
journal "Redémarrage de l'API"
"${COMPOSE[@]}" up -d --no-build --wait --wait-timeout 300 api
journal "Restauration terminée : $SCHEMA remis à l'état de $fichier"