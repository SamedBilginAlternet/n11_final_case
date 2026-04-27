#!/usr/bin/env bash
# Bootstrap a fresh Ubuntu 22.04/24.04 DigitalOcean droplet for n11-final-case.
# Run as root: ssh root@DROPLET_IP 'bash -s' < setup-droplet.sh

set -euo pipefail

echo "==> Updating apt"
apt-get update -y
apt-get upgrade -y

echo "==> Installing Docker"
if ! command -v docker >/dev/null 2>&1; then
  apt-get install -y ca-certificates curl gnupg
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  source /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
       https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" \
       > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

echo "==> Creating deploy user"
id deploy >/dev/null 2>&1 || useradd -m -s /bin/bash deploy
usermod -aG docker deploy

echo "==> Preparing /opt/n11"
mkdir -p /opt/n11/postgres-init
chown -R deploy:deploy /opt/n11

echo "==> Configuring firewall"
ufw allow OpenSSH || true
ufw allow 80/tcp || true
ufw allow 443/tcp || true
ufw --force enable || true

echo
echo "Next steps:"
echo "  1. As deploy user, copy /opt/n11/.env (use infra/digitalocean/.env.example as template)"
echo "  2. Place docker-compose.prod.yml, Caddyfile, postgres-init/ in /opt/n11/"
echo "  3. Run: cd /opt/n11 && docker compose -f docker-compose.prod.yml --env-file .env up -d"
echo
echo "Done."
