#!/usr/bin/env bash
# Vérifie la préparation de la VM de production de MécaDétect (tâche 5.6).
# À lancer sur la VM : sudo bash verifier-serveur.sh
# Ne modifie rien : se contente de constater.

# Les contrôles sont des fonctions appelées par verifier "…" nom_de_fonction
# shellcheck disable=SC2329

set -u

VERIFICATIONS_ATTENDUES=27
UTILISATEUR=ubuntu
DOSSIER=/opt/mecadetect
SAUVEGARDES=$DOSSIER/sauvegardes
OCI=/home/$UTILISATEUR/.local/bin/oci
BUCKET=mecadetect-sauvegardes

reussites=0
echecs=0

# verifier "libellé" commande [arguments…] : la vérification réussit si la commande réussit
verifier() {
    local libelle=$1
    shift
    if "$@" >/dev/null 2>&1; then
        printf '  \e[32mOK\e[0m     %s\n' "$libelle"
        reussites=$((reussites + 1))
    else
        printf '  \e[31mÉCHEC\e[0m  %s\n' "$libelle"
        echecs=$((echecs + 1))
    fi
}

titre() { printf '\n\e[36m== %s ==\e[0m\n' "$1"; }

# Valeur effective d'un réglage de sshd (fichiers de configuration fusionnés)
sshd_vaut() { [[ $(sshd -T 2>/dev/null | awk -v cle="$1" '$1 == cle { print $2; exit }') == "$2" ]]; }

# Valeur effective d'un réglage d'APT
apt_vaut() { apt-config dump | grep -qF "$1 \"$2\";"; }

# ------------------------------------------------------------------ Contrôles
ubuntu_arm() {
    [[ $(uname -m) == aarch64 ]] && grep -q '^VERSION_ID="24.04"' /etc/os-release
}

fuseau_paris() {
    [[ $(timedatectl show -p Timezone --value) == Europe/Paris ]]
}

swap_actif() {
    local octets
    octets=$(swapon --show=SIZE --bytes --noheadings | awk '{ s += $1 } END { print s + 0 }')
    [[ $octets -ge 2000000000 ]] && grep -qE '^/swapfile[[:space:]]' /etc/fstab
}

mises_a_jour_auto() {
    systemctl is-enabled --quiet unattended-upgrades \
        && apt_vaut APT::Periodic::Update-Package-Lists 1 \
        && apt_vaut APT::Periodic::Unattended-Upgrade 1
}

redemarrage_nocturne() {
    apt_vaut Unattended-Upgrade::Automatic-Reboot true \
        && apt_vaut Unattended-Upgrade::Automatic-Reboot-Time 04:30
}

ssh_sans_mot_de_passe() {
    sshd_vaut passwordauthentication no && sshd_vaut kbdinteractiveauthentication no
}

fail2ban_ssh() {
    systemctl is-active --quiet fail2ban && fail2ban-client status sshd
}

docker_actif() {
    systemctl is-active --quiet docker && systemctl is-enabled --quiet docker \
        && docker version --format '{{.Server.Version}}'
}

# Le plugin « docker compose » (v2 et suivantes : v5 en 2026), pas l'ancien docker-compose v1
compose_plugin() {
    local majeure
    majeure=$(docker compose version --short | sed -E 's/^v?([0-9]+).*/\1/')
    [[ $majeure =~ ^[0-9]+$ && $majeure -ge 2 ]]
}

journaux_bornes() {
    [[ $(docker info --format '{{.LoggingDriver}}') == json-file ]] \
        && grep -qE '"max-size": *"10m"' /etc/docker/daemon.json \
        && grep -qE '"max-file": *"3"' /etc/docker/daemon.json
}

live_restore() {
    [[ $(docker info --format '{{.LiveRestoreEnabled}}') == true ]]
}

groupe_docker() {
    id -nG "$UTILISATEUR" | tr ' ' '\n' | grep -qx docker
}

conteneur_arm() {
    docker run --rm --pull=missing hello-world | grep -q 'Hello from Docker'
}

dossier_deploiement() {
    [[ $(stat -c '%U:%G %a' "$DOSSIER") == "$UTILISATEUR:$UTILISATEUR 750" ]]
}

# Script présent, exécutable, et planifié chaque nuit à 03:00 pour ubuntu
reinitialisation_planifiee() {
    [[ -x $DOSSIER/reinitialiser-demo.sh ]] \
        && crontab -u "$UTILISATEUR" -l 2>/dev/null | grep -qE '^0 3 \* \* \* .*reinitialiser-demo\.sh'
}

# ------------------------------------------------------------------ Sauvegardes (tâche 5.8)
# oci exécuté comme le fera cron : par ubuntu, authentifié par principal d'instance
oci_ubuntu() {
    sudo -u "$UTILISATEUR" -H env OCI_CLI_AUTH=instance_principal \
        OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True "$OCI" "$@"
}

namespace_attendu() { sed -n 's/^SAUVEGARDE_NAMESPACE=//p' "$DOSSIER/.env.prod"; }

oci_installe() { [[ -x $OCI ]] && sudo -u "$UTILISATEUR" -H "$OCI" --version; }

# La VM obtient son identité d'Oracle Cloud, sans clé, et vise le bon namespace
principal_instance() {
    local attendu
    attendu=$(namespace_attendu)
    [[ -n $attendu && $(oci_ubuntu os ns get --query data --raw-output) == "$attendu" ]]
}

bucket_accessible() {
    oci_ubuntu os object list --namespace "$(namespace_attendu)" --bucket-name "$BUCKET" --limit 1
}

dossier_sauvegardes() {
    [[ $(stat -c '%U:%G %a' "$SAUVEGARDES") == "$UTILISATEUR:$UTILISATEUR 700" ]]
}

sauvegarde_planifiee() {
    [[ -x $DOSSIER/sauvegarder.sh ]] \
        && crontab -u "$UTILISATEUR" -l 2>/dev/null | grep -qE '^30 2 \* \* \* .*sauvegarder\.sh'
}

test_restauration_planifie() {
    [[ -x $DOSSIER/restaurer.sh ]] \
        && crontab -u "$UTILISATEUR" -l 2>/dev/null | grep -qE '^45 2 \* \* 0 .*restaurer\.sh --test'
}

derniere_sauvegarde() {
    find "$SAUVEGARDES" -maxdepth 1 -name 'mecadetect-*.dmp.gz' -printf '%f\n' 2>/dev/null | sort | tail -n 1
}

# Moins de 26 heures : la sauvegarde de la nuit dernière a bien eu lieu
sauvegarde_recente() {
    local fichier
    fichier=$(derniere_sauvegarde)
    [[ -n $fichier ]] && [[ -n $(find "$SAUVEGARDES/$fichier" -mmin -1560) ]] \
        && (cd "$SAUVEGARDES" && sha256sum --quiet -c "$fichier.sha256")
}

sauvegarde_externalisee() {
    local fichier
    fichier=$(derniere_sauvegarde)
    [[ -n $fichier ]] && oci_ubuntu os object head --namespace "$(namespace_attendu)" \
        --bucket-name "$BUCKET" --name "$fichier"
}

# Une sauvegarde jamais restaurée ne prouve rien : test réussi depuis moins de 8 jours
test_restauration_recent() {
    [[ -n $(find "$SAUVEGARDES/.dernier-test-restauration" -mtime -8 2>/dev/null) ]]
}

# Libres, ou déjà pris par un conteneur (Caddy une fois déployé)
ports_web_disponibles() {
    ! ss -Hltn '( sport = :80 or sport = :443 )' | grep -q . \
        || docker ps --format '{{.Ports}}' | grep -qE ':(80|443)->'
}

# ------------------------------------------------------------------ Exécution
if [[ $EUID -ne 0 ]]; then
    echo "À lancer avec sudo : sudo bash $0" >&2
    exit 2
fi

titre 'Système'
verifier 'Ubuntu 24.04 sur processeur ARM (aarch64)' ubuntu_arm
verifier 'fuseau horaire Europe/Paris' fuseau_paris
verifier 'swap de 2 Go au moins, actif et déclaré dans /etc/fstab' swap_actif

titre 'Mises à jour'
verifier 'mises à jour de sécurité automatiques (unattended-upgrades)' mises_a_jour_auto
verifier 'redémarrage automatique à 04:30 si une mise à jour le demande' redemarrage_nocturne

titre 'SSH'
verifier 'aucune connexion par mot de passe' ssh_sans_mot_de_passe
verifier 'connexion root interdite' sshd_vaut permitrootlogin no
verifier "seul l'utilisateur $UTILISATEUR peut se connecter" sshd_vaut allowusers "$UTILISATEUR"
verifier 'fail2ban actif et surveillant SSH' fail2ban_ssh

titre 'Docker'
verifier 'Docker Engine installé, actif et lancé au démarrage' docker_actif
verifier 'Docker Compose (plugin, v2 ou plus récent)' compose_plugin
verifier 'journaux des conteneurs bornés par défaut (10 Mo × 3)' journaux_bornes
verifier 'live-restore : les conteneurs survivent au redémarrage du démon Docker' live_restore
verifier "$UTILISATEUR dans le groupe docker" groupe_docker
verifier 'un conteneur arm64 démarre (hello-world)' conteneur_arm

titre 'Déploiement'
verifier "dossier $DOSSIER à $UTILISATEUR, fermé aux autres (750)" dossier_deploiement
verifier 'ports 80 et 443 disponibles pour Caddy' ports_web_disponibles
verifier 'remise à zéro de la démonstration planifiée chaque nuit à 03:00' reinitialisation_planifiee

titre 'Sauvegardes'
verifier 'outil oci installé pour ubuntu' oci_installe
verifier "la VM s'authentifie par principal d'instance (namespace de .env.prod)" principal_instance
verifier "bucket $BUCKET accessible" bucket_accessible
verifier "dossier $SAUVEGARDES à $UTILISATEUR, privé (700)" dossier_sauvegardes
verifier 'sauvegarde planifiée chaque nuit à 02:30' sauvegarde_planifiee
verifier 'test de restauration planifié chaque dimanche à 02:45' test_restauration_planifie
verifier 'sauvegarde de moins de 26 heures, empreinte SHA-256 valide' sauvegarde_recente
verifier 'cette sauvegarde est aussi dans le bucket' sauvegarde_externalisee
verifier 'test de restauration réussi depuis moins de 8 jours' test_restauration_recent

total=$((reussites + echecs))
echo
if [[ $total -ne $VERIFICATIONS_ATTENDUES ]]; then
    echo "$total vérifications au lieu de $VERIFICATIONS_ATTENDUES : le script est incomplet."
    exit 1
fi
if [[ $echecs -eq 0 ]]; then
    printf '\e[32m%d vérifications, toutes réussies.\e[0m\n' "$total"
    exit 0
fi
printf '\e[31m%d vérifications, %d en échec.\e[0m\n' "$total" "$echecs"
exit 1