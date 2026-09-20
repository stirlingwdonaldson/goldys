# Testing

Reproducible verification for the Goldy's platform. Every command below was run
against the current tree; re-run them after any change that touches the backend,
frontend, or database.

## Prerequisites

- **JDK 25** — the committed Gradle wrapper targets Java 25.
- **Docker** — Testcontainers starts PostgreSQL 16; the current user must be able
  to reach the daemon.
- **Bun 1.4.2** — matches `frontend/package.json` `packageManager`.

Integration tests run against PostgreSQL 16 (`postgres:16-alpine`). No test falls
back to an in-memory database; the suite depends on real JSONB, `BYTEA`, partial
indexes, and PostgreSQL triggers.

## Backend (`backend/`)

```bash
./gradlew test             # unit + PostgreSQL integration tests
./gradlew spotlessCheck    # google-java-format check
./gradlew build            # full build
```

Testcontainers pins docker-java to API 1.40 by default (see `build.gradle`), which
keeps the client compatible with Docker 29+; override with `-Dapi.version=<version>`
if a specific environment requires it.

## Frontend (`frontend/`)

```bash
bun install --frozen-lockfile
bun run typecheck
bun run lint
bun run build
```

### Application shell (verified)

The Phase 1 shell and non-intrusive error states were verified as follows:

- `bun run typecheck`, `bun run lint`, and `bun run build` all pass; the build
  emits routes for `/`, `/dashboard`, `/reconciliation`, `/connectors`, and
  `/settings`.
- The root route redirects (`307`) to `/dashboard`, and all four screens render
  their honest empty states (no fabricated operational data).
- The typed API client's error paths were exercised against the dev server with
  the backend stopped: a non-JSON error body resolves to `UNPARSEABLE_RESPONSE`,
  a refused connection to `NETWORK_ERROR`, and a missing
  `correlationId`/`fields` normalizes without a crash.

The Dashboard, Reconciliation, Connectors, and Settings screens are honest
empty states until the later connector/reconciliation vertical slices land; they
are not yet backed by operational data.

No real browser rendered the UI in this environment (no Chrome binary is
installed), so the interactive surfaces — the sidebar active-state highlight,
the loading skeleton, the toast, the permission-denied and error cards — are
built and type-checked but not exercised by an automated browser pass. The typed
client's error branches were verified at runtime against the dev server.


## Local database

```bash
docker compose config     # validate docker-compose.yml
docker compose up         # PostgreSQL 16 on host port 5433
```

Flyway applies migrations V1–V4 on startup, and `hibernate.ddl-auto: validate`
confirms the entities match the schema. The migrations are written for the
cleared rebuild and expect an empty database; recreate a disposable database
rather than migrating one that predates this work.

## OIDC (deployment configuration)

Authentication uses the venue's existing OIDC provider; the client registration
is a gated deployment input, not a source-controlled value. Once the provider
details are known, supply them as environment variables:

```bash
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOLDYS_CLIENT_ID=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOLDYS_CLIENT_SECRET=...
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_GOLDYS_ISSUER_URI=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOLDYS_SCOPE=openid,profile,email
```

Integration tests use mocked OIDC identities and do not require a live provider.
