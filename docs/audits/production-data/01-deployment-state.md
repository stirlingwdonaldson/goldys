# 01 — Deployment state

**Verified production fact, 2026-10-09 ~02:55 UTC:** Docker project `goldys-prod`
is running on this host. Database `goldys` uses **`public`**, not a `goldys` schema.
PostgreSQL reports **16.15**. This establishes operational context before data-quality interpretation.

| Service | Image identifier (actual container) | State |
|---|---|---|
| backend | `sha256:3899b0be6b8b2c63edc08be168963ef3b5e689a04d0613799809234ee2067a56` | Running; no Docker healthcheck |
| frontend | `sha256:7357decc9b6060f46cfb1347d81dff59b83a6a7b2036af570ee6f2d3d2fb4173` | Running; no Docker healthcheck |
| postgres | `postgres:16`, actual `sha256:a3b7f434b2dc57ce85a67e171163eb8ab1a1ebcb39d27484661f26b1dfbe30d6` | Running, healthy |
| sftp | `atmoz/sftp:alpine` | Running; no independently verified protocol health |

Backend/frontend started at 02:31:23 UTC; PostgreSQL started 2026-10-08 10:13:52 UTC.
Backend/frontend have no bind mounts. Compose deployment checkout is
`/srv/ai/projects/goldys`; this audit is in `/tmp/opencode/goldys-production-data-audit`.
No production-managed application files were edited.

## Release provenance

Both application containers report `SENTRY_RELEASE=c3377bb`. This is **release metadata**,
not cryptographic proof of build contents. Audit checkout is
`f6140b8e2fa13d0c26f1e168c2dfe0bc689e0a18`. The Git diff from `c3377bb` to this checkout
touches only Sentry example URLs, Compose comments and frontend monitoring tunnel defaults/tests.
Backend data-processing sources are identical between these refs. Image labels do not supply an
application Git revision. Container/image hashes are recorded above to avoid assuming mutable tags identify releases.

## Database and migrations

- Effective JDBC target: `postgres:5432/goldys`; Spring profile `prod`.
- Search path: `"$user", public`. Only non-system schema found: `public`.
- Flyway **V1–V31**, all `success=true`; newest V31 installed 2026-10-08 13:39:54 UTC.
- Current backend startup logs: “Successfully validated 31 migrations”, “Schema public is up to date”.
- Schema metadata and full applied history: `evidence/schema.csv`, probes S01–S05 in `checks/schema.sql`.
- Inspection connection uses container-configured `goldys` role, which is a **superuser**.
  Enforced session/transaction read-only mode was verified; this is not a least-privilege audit role.
  All probes use 8-second statement, 1-second lock and 15-second idle-transaction timeouts.

## Configuration without credentials

Verified container environment: CTB login configured; CTB drop token configured;
CTB SFTP enabled, poll cron `0 */15 * * * *`, remote directory `/upload`;
Lightspeed webhook token configured; OpenTable drop token configured.
AI chat is `openai`, model `gpt-4o-mini`, key configured. No AI request was made.
`APP_LIGHTSPEED_BACKFILL` is empty. No Deputy-specific credential appears in the inspected environment.
Presence of a credential does not establish external connectivity or source-side delivery.

Checked code defaults: CTB web pull enabled at 04:00 Australia/Melbourne
(`backend/src/main/resources/application.yml:126–144`); SFTP polling uses the above environment.
Actual run history and input paths are assessed in report 03.

## Logs and exposure

At inspection, the current backend container's retained log had 59 lines: one WARN,
no ERROR, no exception-class matches. This covers only the new container, not historical deployments.
Absence of errors is not evidence that all ingested data was transformed.
Only frontend is published, at host port 3000; backend 8080 and PostgreSQL are internal.
No services were restarted and no jobs, migration validation command or ingestion endpoint was invoked.

Frontend live/demo selection and safe HTTP verification are recorded in report 09;
container `NODE_ENV=production` alone does not establish live mode.

## Subsequent read-only verification

Deployed JAR manifest: application `0.1.0-SNAPSHOT`, Spring Boot **3.5.0**, build JDK **25**.
No `git.properties`/build-info commit stamp found. Application YAML files read from the deployed JAR
match checkout bytes exactly. Frontend BUILD_ID: `sMnma63DXhth4pBn9fm27`.
Deployed browser bundle initialises demo state false, verifying **default live mode**; per-browser overrides
remain possible and uninspected. Safe HTTP GET `/api/health` returned **200 JSON UP**.
Data API GETs returned **302 login redirects**, so authenticated data/rendering is unknown.
See `evidence/http-read-checks.json` and report 09.
