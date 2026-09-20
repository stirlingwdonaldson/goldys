# Deployment Environments & Domain Serving Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Containerize both apps, add a production Docker Compose stack serving `platform.swd.sh` from one exposed port (edge-terminated HTTPS), and separate dev/prod configuration with no secrets in the repo.

**Architecture:** Two multi-stage Dockerfiles (backend Spring Boot fat jar on JRE 25; frontend Next.js standalone server on Node 22) run with Postgres in `docker-compose.prod.yml`. The frontend is the only published service and proxies `/api`, `/oauth2`, `/login/oauth2` to the backend over the internal network. A Spring `prod` profile sets a `Secure` cookie and an explicit OIDC redirect URI.

**Tech Stack:** Java 25, Spring Boot 3.5, Gradle 9.5.0, PostgreSQL 16, Next.js 15, Bun 1.4.2 (build) / Node 22 (runtime), Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-20-deployment-environments-design.md`

## Global Constraints

- No application feature, schema, or auth change — infra/config only.
- Keep dev workflow intact: `docker compose up` (Postgres) + `./gradlew bootRun` + `bun run dev`.
- Never commit a real `.env`; only `.env.example` (placeholders). Secrets are env-var driven and absent from image layers.
- One exposed origin; backend and Postgres stay internal (no host port mapping).
- No reverse proxy or TLS terminator inside the VM — the operator's edge owns that.
- Pin exact image bases: `eclipse-temurin:25-jdk` / `eclipse-temurin:25-jre`, `oven/bun:1.4.2-alpine`, `node:22-alpine`, `postgres:16`.
- This environment has no Docker daemon access (`docker info` is permission-denied). Verification therefore uses `docker compose config` (client-side) and build/lint/typecheck; `docker build` and `./gradlew test` (Testcontainers) are the operator's/CI's steps, not run here.

## Review Focus

1. `.env.prod` omits a required secret (`POSTGRES_PASSWORD`, `OIDC_*`) → compose fails loudly via `${VAR:?...}`, never a silent default. Pinned in Task 3.
2. A Docker build context leaks `node_modules`/`.next` (frontend) or `build`/`.gradle` (backend) → `.dockerignore` excludes them. Pinned in Tasks 1/2.
3. `output: "standalone"` breaks the existing `bun run build` or dev server → Task 1 verifies both.
4. OIDC login redirects to the wrong URI behind the edge → explicit `redirect-uri` in the prod profile, not header-derived. Pinned in Task 2 + Task 5 docs.
5. Backend boots in prod with empty OIDC/Datasource config → required compose vars surface it before startup. Pinned in Task 3.

---

### Task 1: Frontend standalone output and Dockerfile

**Files:**
- Modify: `frontend/next.config.ts`
- Create: `frontend/Dockerfile`
- Create: `frontend/.dockerignore`
- Create: `frontend/public/.gitkeep` (empty file)

**Interfaces:**
- Consumes: existing `BACKEND_ORIGIN` rewrite in `next.config.ts`.
- Produces: a frontend image whose entrypoint is `node server.js` (standalone), listening on `3000`, proxying `/api` and `/oauth2` to `http://backend:8080` by default (overridable at build via `--build-arg BACKEND_ORIGIN=...`).

- [ ] **Step 1: Add `output: "standalone"` to `frontend/next.config.ts`**

Replace the file with:

```ts
import type { NextConfig } from "next";

const backendOrigin = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Standalone output produces a self-contained `server.js` the Docker runtime
  // image runs under Node — no dev server, no Bun needed at runtime.
  output: "standalone",
  // Proxy API calls and the OIDC login/callback flows to the Spring Boot
  // backend so the browser's same-origin session cookies reach it. Defaults to
  // local dev; set BACKEND_ORIGIN when frontend and backend aren't co-located
  // behind one origin, or drop these rewrites in a deployment that already
  // routes /api and /oauth2 at the edge.
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendOrigin}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${backendOrigin}/oauth2/:path*` },
      { source: "/login/oauth2/:path*", destination: `${backendOrigin}/login/oauth2/:path*` },
    ];
  },
};

export default nextConfig;
```

- [ ] **Step 2: Write `frontend/Dockerfile`**

```dockerfile
# syntax=docker/dockerfile:1
# Bun compiles the app; a minimal Node image runs the standalone server. Bun is a
# build-only dependency — Next.js `output: "standalone"` ships a `server.js` that
# runs under Node, so the runtime image carries no Bun.

# ---- build ----
FROM oven/bun:1.4.2-alpine AS build
WORKDIR /app

# Install dependencies first so this layer caches across source-only changes.
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile

COPY . .

# The /api and /oauth2 rewrites target the backend service. This is baked at
# build time because Next.js resolves rewrites when the config is loaded.
ARG BACKEND_ORIGIN=http://backend:8080
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN
RUN bun run build

# ---- runtime ----
FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Standalone server + static assets. `public/` is empty today (see .gitkeep) but
# the copy is kept so real public assets work without a Dockerfile change.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
```

- [ ] **Step 3: Write `frontend/.dockerignore`**

```dockerignore
node_modules
.next
.git
.env*
npm-debug.log*
```

- [ ] **Step 4: Create the empty `frontend/public/` directory**

```bash
mkdir -p frontend/public && touch frontend/public/.gitkeep
```

- [ ] **Step 5: Verify the standalone build still passes**

```bash
cd frontend && bun run typecheck && bun run lint && bun run build
test -f .next/standalone/server.js && echo "standalone server.js present"
```

Expected: all commands pass and `standalone server.js present` is printed.

- [ ] **Step 6: Commit**

```bash
cd /srv/ai/projects/goldys && git add frontend/next.config.ts frontend/Dockerfile frontend/.dockerignore frontend/public/.gitkeep
git commit -m "feat: containerize frontend with standalone output"
```

---

### Task 2: Backend Dockerfile and production profile

**Files:**
- Create: `backend/Dockerfile`
- Create: `backend/.dockerignore`
- Create: `backend/src/main/resources/application-prod.yml`

**Interfaces:**
- Consumes: the Spring Boot `bootJar` task and the existing env-driven OIDC/datasource config in `application.yml`.
- Produces: a backend image exposing `8080` and running `java -jar app.jar`; a `prod` Spring profile activated via `SPRING_PROFILES_ACTIVE=prod`.

- [ ] **Step 1: Write `backend/Dockerfile`**

```dockerfile
# syntax=docker/dockerfile:1
# Gradle (on JDK 25) compiles the Spring Boot fat jar; a minimal JRE 25 image runs
# it. Tests are intentionally NOT run here — they need Testcontainers/Postgres and
# CI runs them separately.

# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Wrapper + build files first so the Gradle distribution and dependency downloads
# sit in an early, cacheable layer.
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Write `backend/.dockerignore`**

```dockerignore
.gradle
build
.git
```

- [ ] **Step 3: Write `backend/src/main/resources/application-prod.yml`**

```yaml
# Production Spring profile (SPRING_PROFILES_ACTIVE=prod).
#
# HTTPS is terminated at the operator's edge, so the session cookie must be
# Secure — the browser only ever talks to https://platform.swd.sh. The OIDC
# redirect URI is set explicitly rather than derived from forwarded headers,
# because the edge -> frontend -> backend hop makes header-based derivation
# fragile; OIDC_REDIRECT_URI defaults to the production domain.

server:
  servlet:
    session:
      cookie:
        secure: true
        same-site: lax

spring:
  security:
    oauth2:
      client:
        registration:
          goldys:
            redirect-uri: ${OIDC_REDIRECT_URI:https://platform.swd.sh/login/oauth2/code/goldys}
```

- [ ] **Step 4: Verify the jar still builds and formatting holds**

```bash
cd backend && ./gradlew bootJar spotlessCheck --no-daemon
```

Expected: `BUILD SUCCESSFUL`. (`./gradlew test` is not run here — Testcontainers
needs the Docker daemon, which this environment lacks; the change is additive and
does not affect the test suite.)

- [ ] **Step 5: Commit**

```bash
cd /srv/ai/projects/goldys && git add backend/Dockerfile backend/.dockerignore backend/src/main/resources/application-prod.yml
git commit -m "feat: containerize backend and add production profile"
```

---

### Task 3: Production Compose stack and env template

**Files:**
- Create: `docker-compose.prod.yml`
- Create: `.env.example`
- Modify: `.gitignore`
- Modify: `docker-compose.yml` (pointer comment only)

**Interfaces:**
- Consumes: the images built by Tasks 1–2, and `BACKEND_ORIGIN=http://backend:8080` baked into the frontend image.
- Produces: a `docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build` command; required-variable enforcement via `${VAR:?...}`.

- [ ] **Step 1: Write `docker-compose.prod.yml`**

```yaml
# Production stack for this VM. HTTPS is terminated at the operator's edge, which
# forwards platform.swd.sh to ${PORT:-3000}. Only the frontend is published;
# backend and Postgres live on the internal network.
#
#   cp .env.example .env.prod    # fill in real values
#   docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build

services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-goldys}
      POSTGRES_USER: ${POSTGRES_USER:-goldys}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env.prod}
    volumes:
      - goldys_pg_data:/var/lib/postgresql/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 12

  backend:
    build:
      context: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-goldys}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-goldys}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOLDYS_CLIENT_ID: ${OIDC_CLIENT_ID:?set OIDC_CLIENT_ID in .env.prod}
      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOLDYS_CLIENT_SECRET: ${OIDC_CLIENT_SECRET:?set OIDC_CLIENT_SECRET in .env.prod}
      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOLDYS_SCOPE: ${OIDC_SCOPE:-openid,profile,email}
      SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_GOLDYS_ISSUER_URI: ${OIDC_ISSUER_URI:?set OIDC_ISSUER_URI in .env.prod}
      OIDC_REDIRECT_URI: ${OIDC_REDIRECT_URI:-https://platform.swd.sh/login/oauth2/code/goldys}
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
    environment:
      PORT: "3000"
    ports:
      - "${PORT:-3000}:3000"
    restart: unless-stopped
    depends_on:
      - backend

volumes:
  goldys_pg_data:
```

- [ ] **Step 2: Write `.env.example`**

```bash
# Goldy's environment template. Copy to `.env.prod` (production) and fill in real
# values. Never commit a real env file — these are placeholders only.

# ---- Production (docker compose -f docker-compose.prod.yml) ----
# Host port the operator's edge forwards platform.swd.sh to.
PORT=3000

# Database (required).
POSTGRES_DB=goldys
POSTGRES_USER=goldys
POSTGRES_PASSWORD=

# OIDC identity provider (required). Deployment configuration, never source-controlled.
OIDC_CLIENT_ID=
OIDC_CLIENT_SECRET=
OIDC_ISSUER_URI=
# Optional overrides.
OIDC_SCOPE=openid,profile,email
OIDC_REDIRECT_URI=https://platform.swd.sh/login/oauth2/code/goldys
```

- [ ] **Step 3: Ignore real env files in `.gitignore`**

Append to the root `.gitignore`:

```gitignore
# Local environment files (secrets). .env.example is the committed template.
.env
.env.*
!.env.example
```

- [ ] **Step 4: Add a pointer comment to `docker-compose.yml`**

Insert after the header comment (line 1) of `docker-compose.yml`:

```yaml
# Development only. For the production stack (backend + frontend + Postgres,
# served from platform.swd.sh), see docker-compose.prod.yml and docs/deployment.md.
```

- [ ] **Step 5: Verify compose config interpolates and enforces required vars**

```bash
cd /srv/ai/projects/goldys

# A filled-in env file validates:
printf 'POSTGRES_PASSWORD=dummy\nOIDC_CLIENT_ID=dummy\nOIDC_CLIENT_SECRET=dummy\nOIDC_ISSUER_URI=https://id.example\n' > /tmp/goldys-prod-check.env
docker compose --env-file /tmp/goldys-prod-check.env -f docker-compose.prod.yml config >/dev/null && echo "filled config OK"
rm /tmp/goldys-prod-check.env

# An empty required var fails loudly (the :? guard):
docker compose --env-file .env.example -f docker-compose.prod.yml config 2>&1 | grep -q "required variable" && echo "missing-var guard OK"
```

Expected: both `filled config OK` and `missing-var guard OK` print. (`:?` fires on
empty as well as unset, so a copied-but-unfilled `.env.prod` fails with a clear
message instead of starting with empty credentials.)

- [ ] **Step 6: Commit**

```bash
cd /srv/ai/projects/goldys && git add docker-compose.prod.yml .env.example .gitignore docker-compose.yml
git commit -m "feat: add production compose stack and env template"
```

---

### Task 4: CI Docker image builds

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: the two Dockerfiles from Tasks 1–2.
- Produces: a `docker` CI job that builds both images (`docker build`, no push) on every push to `main` and every PR.

- [ ] **Step 1: Add the `docker` job to `.github/workflows/ci.yml`**

Append after the `frontend` job:

```yaml
  docker:
    name: Docker images (build)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build backend image
        run: docker build -t goldys-backend:ci ./backend

      - name: Build frontend image
        run: docker build -t goldys-frontend:ci ./frontend
```

- [ ] **Step 2: Verify YAML syntax**

```bash
cd /srv/ai/projects/goldys
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('ci.yml YAML OK')"
```

If `python3`/PyYAML is unavailable, fall back to careful visual review of
indentation. Expected: no YAML error.

- [ ] **Step 3: Commit**

```bash
cd /srv/ai/projects/goldys && git add .github/workflows/ci.yml
git commit -m "ci: build docker images to catch Dockerfile breakage"
```

---

### Task 5: Deployment runbook

**Files:**
- Create: `docs/deployment.md`

**Interfaces:**
- Consumes: everything above.
- Produces: operator-facing runbook for dev and prod, DNS/edge, OIDC, upgrade, and rollback.

- [ ] **Step 1: Write `docs/deployment.md`**

```markdown
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

## OIDC

Register this exact redirect URI with your identity provider:

```
https://platform.swd.sh/login/oauth2/code/goldys
```

Then set in `.env.prod`:

- `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`
- `OIDC_ISSUER_URI`
- `OIDC_REDIRECT_URI` (only if it differs from the default above)

Restart the backend after changing these:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d backend
```

## Database

Postgres data lives in the named volume `goldys_pg_data`. The schema is owned by
Flyway migrations and applied automatically on backend startup. To reset (dev):

```bash
docker compose down -v
```

Do NOT run `down -v` in production — it deletes the database.

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

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
curl -s https://platform.swd.sh/api/health
```

## Secrets

All secrets are supplied via `.env.prod`, which is gitignored. Never commit it,
and never bake credentials into an image or a build arg.
```

- [ ] **Step 2: Verify**

```bash
cd /srv/ai/projects/goldys && git diff --check
```

Expected: clean (no trailing whitespace/conflict markers).

- [ ] **Step 3: Commit**

```bash
cd /srv/ai/projects/goldys && git add docs/deployment.md
git commit -m "docs: add deployment runbook"
```

---

## Checkpoint: Deployment Environments Complete

- [ ] `bun run typecheck && bun run lint && bun run build` pass with `output: "standalone"` and `.next/standalone/server.js` exists.
- [ ] `./gradlew bootJar spotlessCheck` passes (backend jar builds; prod profile is additive).
- [ ] `docker compose --env-file .env.example -f docker-compose.prod.yml config` validates, and a missing required var fails loudly.
- [ ] CI `docker` job builds both images on PR/push.
- [ ] `.gitignore` excludes real `.env` files while `.env.example` stays committed.
- [ ] `docs/deployment.md` covers dev, prod, DNS/edge, OIDC, upgrade, and rollback.
- [ ] Five atomic Conventional Commits; no secrets or unrelated untracked files staged.
