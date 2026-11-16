# Pi in the existing production website

This release replaces Ask Goldy's for the existing owner accounts at
`https://platform.swd.sh`. It uses the existing Spring user table, password hashes,
session cookie and PermissionService. There is no Pi login, new user database,
account import or public Pi website. Default activation remains off until the
operator completes this one-time setup. These commands are deployment instructions,
not evidence that production has been changed.

Core scope: governed reporting, follow-ups/queue, Stop, history/retry, explicit
query-only dashboard saves and the four supported public research topics. Files,
dataset downloads and generated-code tools remain **off in production** pending
file/retention acceptance. The synthetic preview still supports those features.
Manager grants are unchanged. Pre-V120 legacy conversations remain restricted;
new history is private to the owning account. Nothing copies preview accounts,
fixtures, budgets or conversations into production.

## Connection

Browser → existing domain → existing frontend → existing Spring backend.
Pi → localhost:18444 on analysis VM → verified SSH reverse tunnel → main VM
loopback:18444 → dedicated TLS proxy → existing backend worker endpoints.

The public edge returned HTTP 403 to the analysis VM's health request during
preparation. The private bridge avoids relying on public edge rules and adds no
public port. It exposes only `/api/workspace-worker/`; the worker token is checked
by Spring. Browser session cookies and passwords never reach the worker. The
worker receives neither production database nor source credentials.

The production unit conflicts with the preview worker because this VM permits
only one investigation. Switching it stops the preview worker. The existing
sandbox broker may remain installed but is not mounted into the production worker.

## Before merging

Review the entire branch, including additive V114–V123 migrations. Back up the
production database through the normal approved process before deployment. Keep
its existing database/volumes; never run the preview seed script or reset accounts.
CI checks both the default and Pi frontend builds, backend tests, worker image,
bridge contracts and a real isolated TLS-proxy smoke test. Wait for actual PR
checks after pushing; local evidence does not establish remote CI.

## One-time setup after merge, before activation

Run these on the **main VM**, from the normal production deployment checkout.
They prepare dedicated Pi files; do not copy the preview configuration or CA.

1. Create a dedicated bridge certificate and service token. If these files already
   exist, preserve them rather than regenerating a token used by a running worker.

   ```sh
   sudo install -d -m 0700 /etc/goldys-pi /etc/goldys-pi/tls
   sudo sh -c 'test -e /etc/goldys-pi/worker-token || (umask 077; openssl rand -hex 32 > /etc/goldys-pi/worker-token)'
   sudo sh -c 'test -e /etc/goldys-pi/tls/server.key || openssl req -x509 -newkey rsa:3072 -nodes -days 825 -subj /CN=localhost -addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1" -keyout /etc/goldys-pi/tls/server.key -out /etc/goldys-pi/tls/server.crt'
   sudo chown root:101 /etc/goldys-pi/tls/server.key
   sudo chmod 0440 /etc/goldys-pi/tls/server.key
   sudo chmod 0644 /etc/goldys-pi/tls/server.crt
   sudo python3 ops/pi-production/configure-app.py "$PWD/.env.prod"
   sudo cp /etc/goldys-pi/tls/server.crt /tmp/goldys-pi-ca.crt
   ```

   The config helper atomically updates only Pi settings in the existing env file,
   preserves other settings and Compose profiles, and prints no secrets. It sets
   `APP_WORKSPACE_ENABLED=true`, matching frontend/backend flags, the service token,
   `COMPOSE_PROFILES=pi`, and `APP_WORKSPACE_FILES_ENABLED=false`. It does not restart
   anything. The certificate must be renewed and redistributed before its 825-day
   expiry; expiry fails closed.

2. Build the worker bundle and privately transfer it. Only the **public** CA goes
   into the bundle; the TLS private key stays on the main VM.

   ```sh
   ops/pi-production/package.sh /tmp/goldys-pi-production-release /tmp/goldys-pi-ca.crt
   scp -F /dev/null -i /home/stiring/.ssh/id_ed25519_goldys_analysis -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -r /tmp/goldys-pi-production-release goldys@192.168.5.42:/tmp/
   ssh -F /dev/null -i /home/stiring/.ssh/id_ed25519_goldys_analysis -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes goldys@192.168.5.42 'sudo systemctl disable --now goldys-pi-worker; sudo bash /tmp/goldys-pi-production-release/install.sh'
   sudo cat /etc/goldys-pi/worker-token | ssh -F /dev/null -i /home/stiring/.ssh/id_ed25519_goldys_analysis -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes goldys@192.168.5.42 'sudo install -m 0600 /dev/stdin /etc/goldys-agent/production-worker-token'
   ```

   The install script verifies checksums, uses the immutable worker image ID,
   installs the unit without starting it, and refuses to overwrite existing
   production configuration. The existing dedicated DeepSeek key remains at
   `/etc/goldys-agent/deepseek-api-key`; it is not copied or printed. The reviewed
   model/cost bounds and A$5/A$50 caps remain unchanged. Do not reset reservations
   to bypass a cap. Provider invoice reconciliation is still separate work.

3. Install the persistent main-VM tunnel. It uses the already verified SSH key and
   host-key record; it does not disable host verification or change public routing.

   ```sh
   sudo install -m 0644 ops/pi-production/goldys-pi-production-tunnel.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now goldys-pi-production-tunnel
   ```

4. Redeploy the existing app and private bridge, then start the worker:

   ```sh
   docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build backend frontend pi-bridge
   ssh -F /dev/null -i /home/stiring/.ssh/id_ed25519_goldys_analysis -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes goldys@192.168.5.42 'sudo systemctl enable --now goldys-pi-production'
   ```

   The worker checks the HTTPS bridge/token before starting. If Spring is still
   migrating/starting, the unit retries after five seconds without claiming work.
   The helper never follows redirects with the worker token. No new browser URL
   or account is required. A normal backend restart may require signing in again
   with the **same existing password** because sessions are in memory.

## Acceptance after deployment

- Open the normal production domain and sign in with your existing credentials.
- There is one Ask Goldy's button and no synthetic-preview banner.
- Ask a small dated reporting question; compare the result with the existing
  dashboard. Follow up, close/reopen and refresh. Test an explicit dashboard save.
- Confirm Ctrl/Cmd+J and the command palette open the same panel.
- On the analysis VM: `sudo systemctl status goldys-pi-production` and
  `sudo journalctl -u goldys-pi-production -n 50 --no-pager`.
- On the main VM: `sudo systemctl status goldys-pi-production-tunnel`.
- Do not declare production verified until these checks pass with real accounts.

## Subsequent updates and rollback

For updates, pause admission (`APP_WORKSPACE_ACCEPTING_JOBS=false`) and recreate
only the backend, allow jobs to finish or explicitly Stop them, then stop the
production worker. Build/transfer a new bundle, verify `SHA256SUMS`, load its image
and update `GOLDYS_WORKER_IMAGE` in `/etc/goldys-agent/production-worker.conf` to the
new immutable ID. Preserve the token, CA and application origin. Install changed
scripts/units deliberately; the first-install helper refuses existing config.
Restart the worker, verify readiness, then re-enable admission and recreate backend.
Never run preview and production workers together. The firewall refresh is a
startup action for the stopped dedicated worker, not a live rule rotation.

For rollback, pause admission and stop `goldys-pi-production`. Leave backend
workspace activation on so authorized Pi history remains available through its API.
The restored legacy drawer does not display Pi history; re-enable the Pi frontend
when resuming that experience. Build the
frontend with `NEXT_PUBLIC_WORKSPACE_ENABLED=false` to restore the legacy Ask
entry point, while keeping backend `APP_WORKSPACE_ENABLED=true`; run:

```sh
docker compose --env-file .env.prod -f docker-compose.prod.yml -f ops/pi-production/legacy-frontend.yml up -d --build --no-deps frontend
```

This overrides only the frontend build flag. Legacy Ask still needs its
previous model configuration. Do not downgrade migrations, restore the whole
production DB merely to switch engines, or import Pi snapshots into Spring AI.

Local verification and remaining limitations are recorded in the
[rollout runbook](../../docs/operations/pi-workspace-rollout.md). Review remaining
static/image findings and provider data-handling/accounting requirements as part
of release approval; this wiring does not waive them.
