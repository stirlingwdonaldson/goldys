#!/usr/bin/env bash
# Builds only a worker image and a release directory; never touches running services.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
out=${1:?Usage: package.sh /absolute/new/output-directory}
[[ "$out" = /* && ! -e "$out" ]] || { echo 'Use a new absolute output directory.' >&2; exit 1; }
cert=${2:?Usage: package.sh /absolute/new/output-directory /path/to/public/server.crt}
openssl x509 -in "$cert" -noout -checkend 86400 >/dev/null
mkdir -m 0700 -- "$out"
cp "$cert" "$out/production-ca.crt"
docker build --tag goldys-agent-worker:release "$root/agent-worker"
docker image inspect --format '{{.Id}}' goldys-agent-worker:release > "$out/worker-image-id"
docker save --output "$out/worker-image.tar" "$(cat "$out/worker-image-id")"
cp "$root/analysis-sandbox/configure-egress.py" "$out/"
cp "$root/ops/pi-production/"{check-worker.py,install.sh,goldys-pi-production.service} "$out/"
(cd "$out" && sha256sum production-ca.crt worker-image.tar worker-image-id configure-egress.py check-worker.py install.sh goldys-pi-production.service > SHA256SUMS)
echo "Release prepared at $out; transfer it privately to the analysis VM."
