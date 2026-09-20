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
cp .env.example .env.prod                 # fill in POSTGRES_PASSWORD + OIDC_*
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

This builds and runs three containers — `frontend` (published on `${PORT:-3000}`),
`backend`, and `postgres`. Only `frontend` is reachable from the host; it proxies
`/api` and `/oauth2` to `backend` over the internal network.

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

## OIDC (optional)

Login is optional. Without OIDC the app runs and the UI shows "Sign in" — you can
deploy and view the site first, then wire up login later.

To enable login:

1. Register this exact redirect URI with your identity provider:

   ```
   https://platform.swd.sh/login/oauth2/code/goldys
   ```

2. Copy `.env.oidc.example` to `.env.oidc` and fill in the four values
   (`CLIENT_ID`, `CLIENT_SECRET`, `SCOPE`, `ISSUER_URI`).

3. Restart the backend:

   ```bash
   docker compose --env-file .env.prod -f docker-compose.prod.yml up -d backend
   ```

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

## Upgrading

```bash
git pull
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

## Secrets

All secrets are supplied via `.env.prod`, which is gitignored. Never commit it,
and never bake credentials into an image or a build arg.
