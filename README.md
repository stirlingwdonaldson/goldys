# Goldy's Unified Data Platform

Data integration and analytics for Goldy's pub. It pulls the venue's operational
systems into one immutable record, reconciles the places where they disagree, and
serves dashboards, reports and an AI assistant from the reconciled result.

```
raw ledger (append-only, byte-faithful)
  → bitemporal canonical facts
    → reconciliation (rules + manual overrides)
      → resolved projections
        → semantic queries → REST API · dashboards · Ask Goldy's
```

Start with [`docs/README.md`](docs/README.md), the index for everything else.
Architecture decisions live in [`docs/system-context.md`](docs/system-context.md);
scope and acceptance criteria in [`docs/prd.md`](docs/prd.md).

## Layout

- `backend/`: Java 25, Spring Boot 3.5, Spring AI, PostgreSQL 16 (Flyway-owned schema).
  A modular monolith; package boundaries are described in
  [`docs/architecture/current-state.md`](docs/architecture/current-state.md) and
  enforced by `ArchitectureBoundariesTest`.
- `frontend/`: Next.js 15 (App Router), React 19, TypeScript, Tailwind, shadcn/ui,
  Recharts, React Flow. Bun 1.4.2 only.
- `docker-compose.yml`: local Postgres 16 on host port **5433**.
- `docker-compose.prod.yml`: production stack (frontend, backend, postgres, sftp).
- `docs/`: architecture, requirements, connector notes, design system, runbooks,
  audits, and dated design records.

## What exists today

| Area | State |
|---|---|
| Raw ledger | `raw_record` with `BYTEA` payloads, SHA-256 and length, DB-enforced append-only; ingestion runs and failures are first-class (`SUCCESS`/`PARTIAL`/`FAILED`/`NO_NEW_DATA`) |
| Canonical layer | Bitemporal entities for daily sales, product sales, reservations, labour, invoices, invoice lines, stock counts and wastage |
| Reconciliation | Daily and product sales compared across Lightspeed and CTB; source-priority resolution rules with audit; manual overrides per domain; recomputable resolved projections |
| Connectors | Lightspeed (Insights webhooks), CTB (scheduled AJAX pull, invoice CSV + PDF over SFTP), OpenTable (manual CSV drop), Deputy (raw-only webhook) |
| Semantic layer | Metric catalogue ([`docs/metrics/catalog.md`](docs/metrics/catalog.md)) with base and derived metrics, provenance and trust/freshness state |
| Ask Goldy's | Spring AI chat over a fixed, enum-validated tool set; per-metric authorization; persisted threads; renders typed widget specs |
| Dashboards | Overview dashboard plus saved custom dashboards (templates, sharing, revisions), re-rendered live |
| Frontend | Sales, Staff & labour, Reservations, Kitchen, Reconciliation, Resolution rules, Data health, Data explorer, Logs, Conversations, Custom dashboards, Settings |
| Auth | Email + password accounts; table-driven `(department, seniority, resource)` permissions |
| Ops | Docker Compose deploy at `platform.swd.sh`, Sentry (self-hosted), Micrometer/Prometheus |

Not built yet: Smart Exporter (PRD Req. 10), Automation Hub, an automated OpenTable
pull, Deputy canonicalization, MarketMan history import, and a permission admin UI.
[`docs/audits/production-data/`](docs/audits/production-data/README.md) records what
production actually contains and the prioritised gaps.

## Open questions

Two stakeholder inputs gate their dependent behaviour (tracked in
[`docs/prd.md`](docs/prd.md#open-questions)):

- **Field-to-role permission mapping**: which fields each department × seniority
  combination may see. Until it's decided, permissions are seeded for `ALL × OWNER`
  only.
- **Department and seniority values** beyond BOH/FOH/ALL and Staff/Manager/Owner.

## Development

```bash
docker compose up -d                       # Postgres on :5433
cd backend && SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/goldys ./gradlew bootRun
cd frontend && bun install && bun run dev  # http://localhost:3000, proxies /api to :8080
```

Checks (what CI runs, plus Vitest):

```bash
cd backend && ./gradlew test spotlessCheck build
cd frontend && bun run typecheck && bun run lint && bun run test && bun run build
```

See [`docs/operations/testing.md`](docs/operations/testing.md) and
[`docs/operations/deployment.md`](docs/operations/deployment.md).
