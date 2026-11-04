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
scope in [`docs/prd.md`](docs/prd.md), acceptance in [`docs/srs.md`](docs/srs.md),
and current capability/limits in [`docs/app-state.md`](docs/app-state.md).
Outstanding work lives in [GitHub issues](https://github.com/stirlingwdonaldson/goldys/issues),
indexed by [`docs/roadmap.md`](docs/roadmap.md).

## Layout

- `backend/`: Java 25, Spring Boot 3.5, Spring AI, PostgreSQL 16 (Flyway-owned schema).
  A modular monolith; package boundaries are described in
  [`docs/architecture/current-state.md`](docs/architecture/current-state.md) and
  enforced by `ArchitectureBoundariesTest`.
- `frontend/`: Next.js 15 (App Router), React 19, TypeScript, Tailwind, shadcn/ui,
  Recharts, React Flow. Bun 1.4.2 only.
- `docker-compose.yml`: local Postgres 16 on host port **5433**.
- `docker-compose.prod.yml`: production stack (frontend, backend, postgres, sftp, plus the
  `timesfm`, `opentable-browser` and `product-classify` sidecars).
- `docs/`: architecture, requirements, connector notes, design system, runbooks,
  audits, and dated design records.

## What exists today

| Area | State |
|---|---|
| Raw ledger | `raw_record` with `BYTEA` payloads, SHA-256 and length, DB-enforced append-only; ingestion runs and failures are first-class (`SUCCESS`/`PARTIAL`/`FAILED`/`NO_NEW_DATA`) |
| Canonical layer | Bitemporal entities for daily sales, product sales, reservations, labour, invoices, invoice lines, stock counts and wastage |
| Reconciliation | Daily and product sales compared across Lightspeed and CTB; source-priority resolution rules with audit; manual overrides per domain; recomputable resolved projections |
| Connectors | Lightspeed (Insights webhooks), CTB (scheduled AJAX pull, invoice CSV + PDF over SFTP), OpenTable (assisted sign-in + JSON pull, opt-in; manual CSV drop as fallback), Deputy (raw-only webhook) |
| Semantic layer | Metric catalogue ([`docs/metrics/catalog.md`](docs/metrics/catalog.md)) with base and derived metrics, provenance and trust/freshness state |
| Ask Goldy's | Spring AI chat over a fixed, enum-validated tool set; per-metric authorization; persisted threads; renders typed widget specs |
| Dashboards | Overview dashboard plus saved custom dashboards (templates, sharing, revisions), re-rendered live |
| Weekly report | Non-labour weekly history, editable targets, wastage reason coverage and dated stock counts with a matching Excel snapshot export; unresolved fields and accuracy reviews are documented in [`weekly-report-review.md`](docs/operations/weekly-report-review.md) |
| Forecasting | TimesFM 3.0 zero-shot forecasting: pick a target metric + covariate inputs (sources shown), async persisted runs, compare-to-actuals; needs the self-hosted `timesfm` inference service |
| Frontend | Sales, Staff & labour, Reservations, Kitchen, Reconciliation, Resolution rules, Data health, Data explorer, Logs, Conversations, Custom dashboards, Settings |
| Auth | Email + password accounts; table-driven `(department, seniority, resource)` permissions |
| Ops | Docker Compose deploy at `platform.swd.sh`, Sentry (self-hosted), Micrometer/Prometheus |

Remaining target gaps include broader reusable reports/exports, retained issued reports,
business delivery/alerts, OpenTable menu canonicalization (menus remain raw-only),
Deputy canonicalization, and approved manager permissions. Canonical reviews and owner
account administration already exist. MarketMan is a possible one-time history input.
See the [app-state summary](docs/app-state.md) and [issue index](docs/roadmap.md).
[The production-data audit](https://github.com/stirlingwdonaldson/goldys/blob/b2377f979742c74b8504f6ddf57acd3460390025/docs/audits/production-data/README.md) records the 9 October 2026 production snapshot and its prioritised gaps;
it is historical evidence, not the current deployment state.

## Open questions

Stakeholder inputs gate dependent behaviour (tracked in the
[SRS](docs/srs.md#outstanding-decisions) and linked decision issues):

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
