#!/usr/bin/env bash
# Run on the analysis VM only. Installs a release; activation is a separate command.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
sha256sum --check SHA256SUMS
[[ $(id -u) = 0 ]] || { echo 'Run with sudo on the analysis VM.' >&2; exit 1; }
[[ $(hostname) = svc-goldys-agent ]] || { echo 'This release is for svc-goldys-agent only.' >&2; exit 1; }
[[ ! -e /etc/goldys-agent/production-worker.conf ]] || {
  echo 'Existing production configuration is preserved; update the image ID manually after loading the release.' >&2
  exit 1
}
[[ $(cat worker-image-id) =~ ^sha256:[0-9a-f]{64}$ ]] || exit 1
# The shared egress helper must never be changed underneath a running worker.
if systemctl is-active --quiet goldys-pi-worker || systemctl is-active --quiet goldys-pi-production; then
  echo 'Stop/drain the existing Pi worker before installing this release.' >&2; exit 1
fi
docker load --input worker-image.tar
image=$(cat worker-image-id)
docker image inspect "$image" >/dev/null
install -d -m 0755 /opt/goldys-agent
install -d -m 0700 /etc/goldys-agent
install -m 0644 configure-egress.py /opt/goldys-agent/configure-egress.py
install -m 0644 production-ca.crt /opt/goldys-agent/production-ca.crt
install -m 0644 check-worker.py /opt/goldys-agent/check-worker.py
install -m 0644 goldys-pi-production.service /etc/systemd/system/goldys-pi-production.service
printf 'GOLDYS_WORKER_IMAGE=%s\nGOLDYS_SPRING_ORIGIN=https://localhost:18444\n' "$image" > /etc/goldys-agent/production-worker.conf
chmod 0600 /etc/goldys-agent/production-worker.conf
# Prevent two enabled conflicting workers from racing at the next boot.
systemctl disable goldys-pi-worker.service
systemctl daemon-reload
echo 'Release installed, not started. Configure the dedicated token, deploy the main app, then enable goldys-pi-production.'
