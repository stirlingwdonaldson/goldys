# Goldy's Deployment Environments & Domain Serving

**Status:** Draft — for review
**Date:** 2026-09-20
**Scope:** Containerization, environment separation (dev/prod), and domain serving for `platform.swd.sh`. No application feature or schema changes.

## 1. Objective

Make the platform deployable on this VM and servable from `platform.swd.sh` with a
clean dev/prod split. Production runs as Docker Compose containers behind the
repo's single-origin model: one exposed HTTP port (the Next.js frontend) proxies
`/api`, `/oauth2`, and `/login/oauth2` to the Spring Boot backend over an internal
Docker network. HTTPS is terminated at the operator's edge (Cloudflare or similar),
which forwards to the VM's exposed port.

## 2. Sources of Truth and Current Baseline

- Architecture invariants and the single-origin requirement: `docs/system-context.md`.
- OIDC is deployment configuration, never source-controlled values: `docs/prd.md`,
  the Phase 1 spec, and `backend/src/main/resources/application.yml`.
- Current state: `docker-compose.yml` is Postgres-only (dev); there are no
  Dockerfiles; `frontend/next.config.ts` already rewrites `/api`, `/oauth2`, and
  `/login/oauth2` to `BACKEND_ORIGIN` (default `http://localhost:8080`);
  `.github/workflows/ci.yml` runs test/format/build for both apps but builds no images.

## 3. Scope

### In scope

- `backend/Dockerfile` and `frontend/Dockerfile` (multi-stage; production images).
- `docker-compose.prod.yml` running backend + frontend + Postgres.
- `frontend/next.config.ts` `output: "standalone"`.
- `backend/src/main/resources/application-prod.yml` (prod Spring profile).
- Root `.env.example` documenting every variable, and `.gitignore` for real env files.
- A CI job that builds both images (no push) to catch Dockerfile breakage.
- `docs/deployment.md` runbook (dev, prod, DNS/edge, OIDC, upgrade/rollback).

### Out of scope

- Any application feature, schema, or auth change.
- A reverse proxy running inside the VM (operator's edge handles TLS/routing).
- Actual deployment, DNS, or TLS provisioning (no host/edge credentials are in scope).
- Secrets: no real `.env` values are committed; only `.env.example` placeholders.
- Multi-host orchestration (Swarm/K8s).

## 4. Architecture

```text
browser ──HTTPS──▶ edge (operator DNS/Cloudflare) ──HTTP──▶ VM:${PORT}
                                                            │ frontend (Next.js standalone)
                                                            │  /api/* , /oauth2/* , /login/oauth2/*
                                                            ▼
                                                       backend:8080 (internal Docker network)
                                                            │
                                                            ▼
                                                       postgres:5432 (internal)
```

- The frontend is the only publicly reachable service. It proxies API and OIDC
  traffic to the backend via the existing `BACKEND_ORIGIN` rewrite; in production
  that value is `http://backend:8080` (the compose service name), baked at image
  build time.
- Backend and Postgres are not published to the host; they exist only on the
  internal network.
- The exposed host port is `${PORT:-3000}`.

## 5. Environments

### Dev

Unchanged workflow: `docker compose up -d` starts Postgres on host `5433`; the apps
run natively with hot reload (`./gradlew bootRun`, `bun run dev`) against
`localhost:8080`. The existing `docker-compose.yml` gains only a pointer comment.

### Prod

`docker compose -f docker-compose.prod.yml up -d --build` builds and runs the three
containers. Configuration comes from `.env.prod` (gitignored) holding the
production secrets; `.env.example` is the documented template.

## 6. Backend Production Profile

`application-prod.yml` (activated by `SPRING_PROFILES_ACTIVE=prod`) sets:

- `server.servlet.session.cookie.secure: true` and `same-site: lax` — the session
  cookie must be `Secure` because the browser talks HTTPS to the edge.
- An explicit OIDC `redirect-uri` via `OIDC_REDIRECT_URI`, defaulting to
  `https://platform.swd.sh/login/oauth2/code/goldys`. Setting it explicitly (rather
  than deriving from forwarded headers) is the robust choice: the edge terminates
  TLS, the Next.js rewrite is a second hop, and header-forwarding through that
  chain is fragile. The operator registers this exact URI with the identity provider.

The datasource and OIDC client credentials remain env-var-driven (no secrets in
source or image layers).

## 7. Environment Variables

Documented in `.env.example` (dev and prod sections). Production requires at minimum:

| Variable | Purpose |
|---|---|
| `PORT` | Host port the edge forwards to (default `3000`) |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Production database |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` / `OIDC_ISSUER_URI` | Identity provider |
| `OIDC_REDIRECT_URI` | `https://platform.swd.sh/login/oauth2/code/goldys` |

## 8. CI

Add a `docker` job to `.github/workflows/ci.yml` that builds both images with
`push: false` (build-only validation). It runs on every push to `main` and every PR,
so a broken Dockerfile fails CI without shipping anything.

## 9. Verification

- `docker compose -f docker-compose.prod.yml config` validates.
- `docker compose build` (both images) — if the Docker daemon is reachable in this
  environment; otherwise documented as the operator's run step.
- `bun run typecheck && bun run lint && bun run build` stays green after the
  `output: "standalone"` change.
- CI: the new docker job builds both images.

## 10. File Structure

```text
backend/Dockerfile
backend/.dockerignore
backend/src/main/resources/application-prod.yml
frontend/Dockerfile
frontend/.dockerignore
frontend/next.config.ts        (modify: output standalone)
docker-compose.prod.yml
docker-compose.yml             (modify: pointer comment only)
.env.example
.gitignore                     (modify: ignore real .env files)
.github/workflows/ci.yml       (modify: add docker build job)
docs/deployment.md
```

## 11. Boundaries

### Always

- Keep secrets out of the repo and out of image layers (env-var driven).
- One exposed origin; backend/Postgres internal only.
- Keep dev workflow (native apps + compose Postgres) intact.
- Never commit a real `.env`; only `.env.example`.

### Ask first

- Add or change hosting infrastructure beyond this VM.
- Change the OIDC redirect URI, auth, or session architecture.
- Publish any image to a registry.

### Never

- Commit OIDC credentials, tokens, or database passwords.
- Run a TLS terminator or reverse proxy inside the VM (the edge owns that).
