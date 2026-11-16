#!/usr/bin/env bash
# Existing synthetic preview only; never creates or changes production resources.
set -euo pipefail
runtime=/tmp/goldys-pi-preview
checkout=/tmp/goldys-pi-workspace-proposal
units=(goldys-pi-preview-web goldys-pi-preview-backend goldys-pi-preview-tunnel)
start_unit() {
  local unit=$1; shift
  if systemctl --user cat "$unit" >/dev/null 2>&1; then
    systemctl --user start "$unit"
  else
    systemd-run --user --unit="$unit" "$@"
  fi
}
case "${1:-status}" in
  start)
    test -f "$runtime/backend.jar" && test -f "$runtime/backend.conf"
    docker start goldys-pi-preview-db >/dev/null
    start_unit goldys-pi-preview-tunnel --property=Restart=on-failure --property=RestartSec=5 \
      /usr/bin/ssh -F /dev/null -i /home/stiring/.ssh/id_ed25519_goldys_analysis \
      -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes \
      -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
      -N -R 18443:127.0.0.1:18443 goldys@192.168.5.42
    start_unit goldys-pi-preview-backend --working-directory="$runtime" \
      --property="EnvironmentFile=$runtime/backend.conf" --property=MemoryMax=1400M --property=CPUQuota=100% \
      /home/stiring/.local/bin/java -Xmx768m -jar "$runtime/backend.jar"
    ready=false
    for attempt in {1..120}; do
      if curl --silent --fail --cacert "$runtime/tls.crt" https://localhost:18443/api/health >/dev/null; then ready=true; break; fi
      sleep 1
    done
    "$ready" || { echo 'Preview backend did not become healthy; inspect its journal.' >&2; exit 1; }
    # Health can become UP before startup runners finish rebuilding projections.
    preview_invocation=$(systemctl --user show goldys-pi-preview-backend --property=InvocationID --value)
    projected=false
    for attempt in {1..120}; do
      if journalctl --user "_SYSTEMD_INVOCATION_ID=$preview_invocation" --no-pager -o cat | rg -q 'Resolved projections rebuilt from current canonical facts'; then projected=true; break; fi
      sleep 1
    done
    "$projected" || { echo 'Preview projection startup was not confirmed; fixtures were not applied.' >&2; exit 1; }
    docker exec -i goldys-pi-preview-db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$runtime/fixtures.sql"
    start_unit goldys-pi-preview-web --working-directory="$checkout/frontend" \
      --property=MemoryMax=768M --property=CPUQuota=75% \
      --setenv="NODE_EXTRA_CA_CERTS=$runtime/tls.crt" --setenv=BACKEND_ORIGIN=https://localhost:18443 \
      --setenv=NEXT_PUBLIC_WORKSPACE_PREVIEW=true --setenv=NEXT_PUBLIC_DEMO_MODE=false \
      /home/stiring/.bun/bin/bun run start --hostname 127.0.0.1 --port 3117
    ;;
  stop)
    systemctl --user stop "${units[@]}"
    docker stop goldys-pi-preview-db >/dev/null
    ;;
  restart)
    "$0" stop
    "$0" start
    ;;
  status) systemctl --user --no-pager status "${units[@]}" ;;
  logs) journalctl --user -u goldys-pi-preview-backend -u goldys-pi-preview-web -u goldys-pi-preview-tunnel --no-pager -n 100 ;;
  *) echo "Usage: $0 {start|stop|restart|status|logs}" >&2; exit 2 ;;
esac
