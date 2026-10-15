#!/usr/bin/env bash
# Prépare la VM de production de MécaDétect (tâche 5.6).
# À lancer sur la VM : sudo bash preparer-serveur.sh
# Idempotent : peut être relancé sans dommage, chaque étape vérifie l'existant.

set -euo pipefail

UTILISATEUR=ubuntu
DOSSIER=/opt/mecadetect
export DEBIAN_FRONTEND=noninteractive

etape() { printf '\n\e[36m== %s ==\e[0m\n' "$1"; }

if [[ $EUID -ne 0 ]]; then
    echo "À lancer avec sudo : sudo bash $0" >&2
    exit 2
fi

# ------------------------------------------------------------------ Système
etape 'Mise à jour du système'
apt-get update -q
apt-get upgrade -y -q
apt-get install -y -q ca-certificates curl fail2ban python3-systemd unattended-upgrades

etape 'Fuseau horaire'
timedatectl set-timezone Europe/Paris

etape 'Swap de 2 Go'
# Filet de sécurité : sans swap, un pic de mémoire tue un processus (souvent Oracle ou la JVM)
if [[ ! -f /swapfile ]]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
fi
swapon --show=NAME --noheadings | grep -qx /swapfile || swapon /swapfile
grep -qE '^/swapfile[[:space:]]' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
# N'utiliser le swap qu'en dernier recours
echo 'vm.swappiness=10' > /etc/sysctl.d/90-mecadetect.conf
sysctl -q --system

# ------------------------------------------------------------------ Mises à jour
etape 'Mises à jour de sécurité automatiques'
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'CONF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
CONF
# Redémarrage la nuit seulement si une mise à jour (noyau…) l'exige ;
# Docker redémarre ensuite les conteneurs (restart: unless-stopped)
cat > /etc/apt/apt.conf.d/52mecadetect-redemarrage <<'CONF'
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "04:30";
CONF
systemctl enable --now unattended-upgrades

# ------------------------------------------------------------------ SSH
etape 'Durcissement de SSH'
# Les fichiers de sshd_config.d sont lus par ordre alphabétique et la première
# valeur l'emporte : 10- passe avant le 60-cloudimg-settings.conf d'Ubuntu
cat > /etc/ssh/sshd_config.d/10-mecadetect.conf <<CONF
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AllowUsers $UTILISATEUR
CONF
# Ubuntu 24.04 démarre sshd à la demande (ssh.socket) : ce dossier n'existe
# pas toujours, et sshd -t échoue sans lui (« Missing privilege separation directory »)
install -d -m 0755 /run/sshd
sshd -t   # refuse d'aller plus loin si la configuration est invalide
systemctl reload ssh.service 2>/dev/null || systemctl restart ssh.service

etape 'fail2ban pour SSH'
cat > /etc/fail2ban/jail.d/mecadetect.local <<'CONF'
[sshd]
enabled  = true
backend  = systemd
maxretry = 5
findtime = 10m
bantime  = 1h
CONF
systemctl enable fail2ban
systemctl restart fail2ban

# ------------------------------------------------------------------ Docker
etape 'Docker Engine (dépôt officiel de Docker)'
if ! command -v docker >/dev/null; then
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    # shellcheck source=/dev/null
    code=$(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
    cat > /etc/apt/sources.list.d/docker.sources <<CONF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $code
Components: stable
Signed-By: /etc/apt/keyrings/docker.asc
CONF
    apt-get update -q
    apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

etape 'Réglages du démon Docker'
cat > /etc/docker/daemon.json <<'CONF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "live-restore": true
}
CONF
systemctl enable docker
systemctl restart docker

# Le groupe docker donne l'équivalent des droits root : réservé à l'utilisateur
# qui déploie. Effet à la prochaine connexion SSH.
usermod -aG docker "$UTILISATEUR"

# ------------------------------------------------------------------ Déploiement
etape 'Dossier de déploiement'
install -d -o "$UTILISATEUR" -g "$UTILISATEUR" -m 750 "$DOSSIER"

etape 'Terminé'
echo "Déconnecte-toi puis reconnecte-toi (groupe docker), puis lance :"
echo "  sudo bash verifier-serveur.sh"
if [[ -f /var/run/reboot-required ]]; then
    echo
    echo "Un redémarrage est conseillé (nouveau noyau) : sudo reboot"
fi