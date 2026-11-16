# Pi in the existing production website

This release replaces Ask Goldy's for the existing owner accounts at
`https://platform.swd.sh`. It uses the existing Spring user table, password hashes,
session cookie and PermissionService. There is no Pi login, new user database,
account import or public Pi website. PR #269 is merged at `96c9af4db0d2ea059de1183bbfc125a6e97b7f6a`.
On 16 November 2026 the user confirmed production login and a reporting answer;
production is reported at V123 with the worker on the separate analysis VM.
Default configuration remains opt-in. The commands below document the original
setup, not a request to repeat it on the deployed system. See the
[follow-up evidence](../../docs/operations/pi-production-followup-2026-11-16.md)
for the boundary between production evidence and local fixtures.

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

Use the [repeatable update procedure](#repeatable-worker-update-and-worker-rollback)
below for subsequent worker releases and worker rollback. It preserves admission,
drain, immutable image, private TLS and single-worker safeguards.

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

## Read-only monitoring (follow-up release)

The analysis-VM `goldys-pi-monitor.timer` runs every minute, independently of the
worker. It uses the existing backend Sentry project on `ops-sentry`, as documented
in [deployment monitoring](../../docs/operations/deployment.md#error-monitoring-sentry).
It sends four fixed-status cron check-ins: `goldys-pi-worker`, `goldys-pi-bridge`,
`goldys-pi-investigations`, and `goldys-pi-certificate`. Two failed check-ins open an
incident; a missed check-in has a three-minute margin. Missing check-ins also detect
loss of the analysis VM or the timer once the monitors have been registered.
This follows the [Sentry check-in protocol](https://develop.sentry.dev/sdk/check-ins/).

- Worker: both systemd activity and actual production-container running state.
- Bridge: token-authenticated `GET /api/workspace-worker/health` through verified
  private TLS. Tunnel, TLS, backend and token failures all fail this probe closed.
- Investigations: expired running lease, elapsed 15-minute job deadline, or a job
  queued for two minutes with no healthy running job occupying the single worker.
  The endpoint returns only three booleans: acceptingJobs, active, stalled. It does
  not expire jobs, claim work, renew a lease or return IDs/prompts/business records.
- Certificate: the certificate actually served through the tunnel expires within
  30 days. Verification failure also alerts; no insecure TLS fallback exists.

The probes print only fixed status codes. HTTP bodies, subprocess stderr and
exception details never enter probe logs or alerts. Check-in authentication stays
in the transport envelope, outside the alert fields. The monitor runs on the host;
no Sentry credential is mounted into the worker. The existing private HTTP Sentry
transport is supported; use HTTPS where available. Redirects are rejected.

**Activation dependency:** an authorized operator must provision the existing
backend-project DSN as root-only `/etc/goldys-agent/production-sentry-dsn`, using the
normal secret-management process, and verify that these Sentry monitors route to
the established operations recipient. This change has not read the production DSN
or inspected recipient configuration, and has not sent a notification. Do not call
alert delivery accepted until a controlled missed/failed/recovered check-in reaches
that recipient. No recipient has been invented. No DSN means monitoring cannot start
and no delivery is claimed.

After the backend health endpoint and worker scripts are deployed, an authorized
operator enables `goldys-pi-monitor.timer`. Check `systemctl list-timers goldys-pi-monitor.timer`, the fixed-status journal for `goldys-pi-monitor.service`,
and all four Sentry monitors. Confirm a failed bridge probe during an approved
maintenance window and recovery afterwards. Automatic worker/tunnel restart is
unchanged. Mute the four monitors during planned maintenance and unmute afterwards;
leaving them muted is an operational failure, not a healthy check.

## Repeatable worker update and worker rollback

These steps require separate production-operation approval. Keep the previous
verified release bundle until the replacement is accepted. The updater supports
older bundles produced by `package.sh`; run the **new** updater with the old bundle
to roll back. Checksums protect transfer integrity, not authenticity: review and
transfer bundles through the established verified SSH path.

1. Deploy the follow-up backend endpoint first. Pause admission by setting
   `APP_WORKSPACE_ACCEPTING_JOBS=false` through the approved configuration process
   and recreate only the backend. Leave workspace activation and history enabled.
2. Let accepted work finish, or use the owning user's explicit Stop action. On the
   analysis VM verify the drain without claiming work:

   ```sh
   sudo env GOLDYS_SPRING_ORIGIN=https://localhost:18444 python3 /path/to/reviewed-release/check-worker.py --drained
   ```

   This refuses until the backend reports both admission paused and no active
   jobs. Stop/interruption cleanup is a separate authorized operation; this check
   deliberately cannot turn expired work into a completed drain.
3. Stop `goldys-pi-production` through systemd. Keep preview stopped and disabled.
   Package and privately transfer the replacement as above, then run on the analysis VM:

   ```sh
   sudo python3 /path/to/reviewed-release/update-worker.py /path/to/replacement-bundle
   ```

   The helper verifies the complete manifest and immutable image ID, checks both
   units and actual containers are stopped, repeats the drain check, loads the
   image, backs up installed scripts/config, and installs the release. It preserves
   the installed CA, origin and all credentials. It never starts a service or changes
   admission. A lock excludes concurrent updater invocations. Configuration is
   replaced atomically, last. If any operation fails, leave admission paused and
   workers stopped; do not assume the partially installed release is ready.
4. Verify token/TLS readiness with the installed `check-worker.py`, then start only
   `goldys-pi-production`. Check its unit and container state and run the monitor;
   the timer runs without a model call. Re-enable admission only after these pass.
   Verify an authorized reporting answer separately; a ready bridge alone does not
   prove the provider or investigation loop is healthy.
5. For a worker rollback, repeat pause/drain/stop and invoke the same reviewed
   `update-worker.py` with the retained **previous** bundle. It restores that bundle's
   immutable image and worker scripts while preserving credentials and current CA.
   For pre-monitoring bundles it retains the installed health/preflight helper,
   which is compatible with the older worker's unchanged `/ready` contract.
   Timestamped previous scripts/config also remain under `/opt/goldys-agent/rollback/`
   for recovery from partial installation. Do not delete images/backups as part of
   this workflow. The legacy frontend rollback above remains available independently.

The first deployment of this follow-up must install its backend endpoint before
using `--drained` or the monitor. The old `/ready` contract remains unchanged.

## Bridge certificate renewal

This is a planned, paused/drained maintenance operation; no live certificate reload
is hidden in a worker update. Stage a fresh pair on the main VM:

```sh
sudo bash ops/pi-production/renew-certificate.sh /etc/goldys-pi/tls-next
```

The script refuses an existing output directory, creates a private key and public
certificate with the existing localhost SANs, and verifies validity/hostname. It
never changes active files or restarts anything. Keep the private key on the main
VM. Transfer **only** `server.crt` to the analysis VM over the verified SSH path.

After pausing admission, draining and stopping the production worker as above:

1. Preserve the old active certificate/key pair in a root-only backup directory
   on the main VM and the old public CA on the analysis VM. Record public
   certificate SHA-256 fingerprints and expiry dates in the private release record.
2. Install the staged key as `root:101` mode `0440` and certificate mode `0644`
   at `/etc/goldys-pi/tls/server.key` and `server.crt`. Recreate only `pi-bridge`
   using the approved production Compose command. The dedicated tunnel configuration
   and worker token do not change.
3. Install the matching public certificate at
   `/opt/goldys-agent/production-ca.crt` on the analysis VM, mode `0644`.
   Verify its fingerprint matches the staged public certificate. Run the installed
   preflight with `GOLDYS_SPRING_ORIGIN=https://localhost:18444`; it checks the
   actual bridge/token with the new trust root. The certificate probe must pass.
4. Start the production worker, confirm readiness and monitor recovery, then resume
   admission. If any check fails, keep admission paused/worker stopped; restore both
   old bridge files and the old worker CA, recreate only the bridge, and repeat
   readiness verification before restarting the worker. Never bypass verification.

Renewal and alert delivery have synthetic local coverage only. This session did not
rotate production certificates, change service configuration or restart services.
