# Testing

Reproducible verification for the Goldy's platform. These are the commands CI and
contributors run; re-run the relevant ones after any change that touches the backend,
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
bun run test
bun run build
```

`bun run test` runs Vitest (pure-logic and React component tests). It covers the
reconciliation decision/derivation logic and the demo-mode toggle; jsdom is the
test environment.

### Demo mode

Most screens can run against fixtures instead of the backend. `NEXT_PUBLIC_DEMO_MODE`
(default on unless set to `"false"`) picks the default, and the Settings toggle
overrides it per browser (stored in `localStorage` as `goldys-demo-mode`). Both
modes go through the same typed `Api` contract (`frontend/lib/api/types.ts`), with
`demoApi` (`demo.ts`) and `liveApi` (`live.ts`) implementations. A "Demo data" banner
marks fixture data. Fixtures are not production data, so never use them as evidence
of live behaviour.

## Test layout

- `backend/src/test/java/com/goldys/platform/<package>/` mirrors the main packages.
  `*IntegrationTest` classes use `support/PostgresContainerConfiguration` to start a
  real PostgreSQL 16 container.
- `architecture/ArchitectureBoundariesTest` enforces the package dependency rules
  listed in [`../architecture/current-state.md`](../architecture/current-state.md).
  If you add a package or a domain, extend it.
- `DatabaseMigrationTest` applies every Flyway migration to an empty database and
  validates the JPA mappings against the result.
- `src/test/resources/conversational-eval/` and `invoice-pdf-eval/` hold fixture
  sets for the Ask Goldy's tool-selection eval and the invoice PDF extractor eval.
- Frontend tests are colocated `*.test.ts(x)` files run by Vitest.

## CI

`.github/workflows/ci.yml` runs on every pull request and on pushes to `main`:

| Job | Runs |
|---|---|
| Backend | `./gradlew test spotlessCheck build` on Temurin 25 |
| Frontend | `bun install --frozen-lockfile`, then `typecheck`, `lint`, `build` |
| Docker | builds the backend and frontend images |

CI does **not** run `bun run test` yet (see finding F11 in
[`../frontend/audit.md`](../frontend/audit.md)), so run Vitest locally before pushing
frontend changes.

## Local database

```bash
docker compose config     # validate docker-compose.yml
docker compose up -d      # PostgreSQL 16 on host port 5433
```

Flyway applies every migration in `backend/src/main/resources/db/migration` on
startup, and `hibernate.ddl-auto: validate` checks that the entities match the
schema. Migrations assume they own the database from V1 onward, so a database
created before Flyway was introduced must be dropped and recreated.

## Authentication in tests

Authentication is email + password (`user_account`, V7). Integration tests create
accounts and roles directly and do not need any external identity provider. See
[`deployment.md`](deployment.md#accounts-and-the-first-administrator) for how a
real deployment gets its first administrator.
