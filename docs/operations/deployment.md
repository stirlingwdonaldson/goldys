# Deployment

How to run Goldy's locally and in production on this VM, and how to serve it
from `platform.swd.sh`.

## Prerequisites

- Docker 23+ and Docker Compose v2+ (this VM has Docker 29 / Compose v5).
- For dev only: JDK 25, Bun 1.4.2.

## Development

Postgres runs in Docker; the apps run natively for hot reload.

```bash
docker compose up -d                      # Postgres on host 5433
cd backend && ./gradlew bootRun           # backend on :8080
cd frontend && bun run dev                # frontend on :3000, proxies /api to :8080
```

Backend defaults assume a native Postgres on 5432; point the compose'd instance
explicitly if needed:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/goldys ./gradlew bootRun
```

## Production

```bash
cp .env.example .env.prod                 # fill in POSTGRES_PASSWORD and any connector tokens
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

This builds and runs three containers — `frontend` (published on `${PORT:-3000}`),
`backend`, and `postgres`. Only `frontend` is reachable from the host; it proxies
`/api` to `backend` over the internal network.

## Staging / second instance (a different port)

To run an isolated second instance alongside production (e.g. to preview a
feature branch), check out the branch and start it with a distinct project name
and port. The project name is what keeps its containers and database volume
separate from production:

```bash
git checkout <branch>                      # e.g. feature/frontend-data-screens
COMPOSE_PROJECT_NAME=goldys-dev PORT=3001 \
  docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

This runs a full copy on `http://<VM-IP>:3001` with its own database
(`goldys-dev_goldys_pg_data`), leaving `platform.swd.sh` (port 3000) untouched.
Point a separate tunnel/domain (or just the port) at it to preview. To stop it
without touching production:

```bash
COMPOSE_PROJECT_NAME=goldys-dev \
  docker compose --env-file .env.prod -f docker-compose.prod.yml down
```

## Pointing platform.swd.sh at it

1. In your DNS/edge (Cloudflare or equivalent), create an A/AAAA record for
   `platform.swd.sh` pointing at this VM's public IP, with TLS/proxy enabled.
2. Forward (origin rule) to `http://<VM-IP>:${PORT}` where `${PORT}` matches the
   `PORT` in `.env.prod` (default `3000`).
3. Confirm `https://platform.swd.sh/api/health` returns `{"status":"UP"}`.

The backend relies on forwarded headers to build correct absolute URLs (login
redirects). Ensure your edge sends `X-Forwarded-Proto: https` (Cloudflare does by
default) and preserves `X-Forwarded-Host`.

Restrict the origin port: if the edge reaches this VM over a tunnel (e.g.
`cloudflared`), set `BIND_ADDRESS=127.0.0.1` in `.env.prod`. Otherwise leave it on
`0.0.0.0` but firewall the `${PORT}` to the edge's IP ranges only — an open
plain-HTTP origin bypasses TLS and access controls.

After going live, smoke-test a full login and confirm the browser lands back on
`https://platform.swd.sh/` (never `http://backend:8080/...`).

## Accounts and the first administrator

Login is email + password, handled by the backend (`/api/auth/signup`,
`/api/auth/login`, session cookie). There is no external identity provider.

New accounts sign up at `/signup` and start as `ALL × STAFF`, which holds no
permission grants, so they see explicit "not permitted" states until promoted.
Promotion is a manual database edit; there is no admin UI yet:

```sql
UPDATE user_account SET department = 'ALL', seniority = 'OWNER'
WHERE email = 'you@example.com';
```

Permission grants are table-driven `(department, seniority, resource)` rows
seeded by migrations (`V6`, `V13`, `V18`, `V23`, `V26`). Today only `ALL × OWNER` holds
grants; BOH/FOH-specific rows wait on the stakeholder field-to-role mapping (see
`docs/prd.md` Open Questions). No seniority is special-cased in code.

> `.env.oidc.example`, the `/oauth2` rewrites in `frontend/next.config.ts`, and some
> code comments still mention OIDC. They are leftovers from the original OIDC
> design (replaced by `V7`/`V8`) and are not used.

## Database

Postgres data lives in a named Docker volume (`goldys-prod_goldys_pg_data` for the
production stack). The schema is owned by Flyway migrations and applied
automatically on backend startup.

The dev and prod stacks use different Compose project names (`goldys` vs
`goldys-prod`), so their containers and volumes are separate. To reset the dev
database:

```bash
docker compose down -v                      # dev only — safe
```

Never run the equivalent against the prod stack (`docker compose --env-file
.env.prod -f docker-compose.prod.yml down -v`) — it deletes the production
database.

## SFTP drop (invoice ingestion)

CTB's Custom Invoice Export is pushed by CTB over SFTP into a small `sftp`
container in this stack; the backend polls that container and ingests the files.
Both sides share one chrooted user.

Setup:

1. In `.env.prod`, set `SFTP_PASSWORD` (required), and optionally `SFTP_USER`
   (default `ctb`) and `SFTP_PORT` (default `2222`).
2. In CTB's Custom Invoice Export settings, set:
   - **Export Method** = SFTP
   - **Host name** = this VM's public IP (or whatever host CTB can reach)
   - **Port** = `SFTP_PORT` (default 2222)
   - **User name** = `SFTP_USER` (default ctb)
   - **Password** = `SFTP_PASSWORD`
   - **Folder** = empty (files land in the user's root)
3. Set `CTB_SFTP_ENABLED=true` in `.env.prod` to start polling, then deploy.

The backend reaches the drop over the internal network (`sftp:22`), so there are
no separate `CTB_SFTP_HOST`/`CTB_SFTP_PORT`/`CTB_SFTP_USER`/`CTB_SFTP_PASSWORD`
to set — those are derived from the compose wiring. The poll runs on
`CTB_SFTP_CRON` (default `0 */15 * * * *` — every 15 min, Melbourne) or on demand via
`POST /api/connectors/CTB/sftp/run`.

**Security:** the SFTP port is published on the host so CTB can reach it. The
`sftp` user is SFTP-only (chrooted, no shell). If you know CTB's source IP range,
restrict `${SFTP_PORT}` to it in the host firewall; otherwise rely on a strong
`SFTP_PASSWORD` and note the port is internet-facing.

CSVs are canonicalized (header + lines → COGS) and are authoritative. PDFs are stored
raw, then `InvoicePdfEnrichmentService` extracts their line detail and enriches the
matching CSV lines (joined by the CSV's `pdf_filename`); anomalies are raised as
invoice ingest flags rather than dropped.

## Upgrading

```bash
git pull
export SENTRY_RELEASE=$(git rev-parse --short HEAD)   # tags errors with the deployed commit
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

Flyway applies any new migrations on backend start.

## Rollback

```bash
git checkout <last-good-tag-or-sha>
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

Because the raw log is append-only and canonical rows are closed rather than
overwritten, rolling back code is safe; rolling back a Flyway migration is not
(do not revert migrations — add a corrective migration instead).

## Logs & health

Every prod compose subcommand needs `--env-file .env.prod` (the `:?` guards apply
to `config`, `ps`, `logs`, etc., not just `up`):

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend
curl -s https://platform.swd.sh/api/health
```

## Error monitoring (Sentry)

Both apps report to the self-hosted Sentry (`ops-sentry`). Everything is opt-in:
with the `SENTRY_*` values unset the SDKs stay off and the build skips source map
upload, so dev, CI and a bare deploy behave exactly as before.

| Setting (`.env.prod`) | Used by | Notes |
|---|---|---|
| `SENTRY_BACKEND_DSN` | backend | DSN of the `goldys-backend` project |
| `SENTRY_FRONTEND_DSN` | frontend (build + runtime) | DSN of `goldys-frontend`; inlined into the browser bundle |
| `SENTRY_ENVIRONMENT` | both | `production` (default) or e.g. `staging` for a second instance |
| `SENTRY_RELEASE` | both | export the git SHA before `up --build` (see Upgrading) |
| `SENTRY_URL`, `SENTRY_ORG` | frontend build | source map upload; use Sentry's **internal** URL |
| `SENTRY_AUTH_TOKEN` | frontend build | org auth token; passed as a BuildKit secret, never a build arg |
| `SENTRY_FEEDBACK` | frontend build | `true` shows a "Report a problem" button (Sentry User Feedback) |
| `SENTRY_TUNNEL_TARGET` | frontend runtime | optional LAN address the `/monitoring` relay forwards to |

Browser events go to `/monitoring` on this site and are relayed server-side
(`frontend/app/monitoring/route.ts`), because the self-hosted Sentry is plain
HTTP and an HTTPS page cannot call it directly.

What is reported, and what is deliberately not:

- Unhandled backend and frontend errors, plus every **connector failure** (tagged
  `source_system` / `failure_type`, grouped per connector and failure mode). The
  `IngestionFailure` ledger stays the record of truth; Sentry is the alert.
- The CTB 4am pull checks in to the `ctb-scheduled-pull` cron monitor, created
  automatically on first run, so a pull that fails, hangs or never runs alerts.
- Request traces for both apps, linked browser → Next.js → Spring.
- Every error response carries `X-Correlation-ID`, also tagged on the Sentry event.
- Never sent: request/response bodies (uploads, webhooks), cookies, user details,
  stack-frame local variables, or raw exception messages from unclassified
  connector faults (they may contain tokens or payload fragments).

## Secrets

All secrets are supplied via `.env.prod`, which is gitignored. Never commit it,
and never bake credentials into an image or a build arg.
