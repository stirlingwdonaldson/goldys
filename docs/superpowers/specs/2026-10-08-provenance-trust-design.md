# Goldy's Provenance, Trust, and Data-Quality UX Design

**Status:** In review
**Date:** 2026-10-08
**Scope:** Make data trust and provenance a first-class product feature (PASS 9): a consistent trust
model that traces a value from source evidence to the final metric, per-domain freshness rules,
explicit missing-data semantics, trust affordances across the UI, and trust metadata for Ask Goldy's.

This is **sub-project B** of a two-part effort. It builds on sub-project A (Ask Goldy's analytical
expansion), whose `MetricProvenance` envelope, `ConnectorHealthQuery` seam, and per-metric permission
model are the hooks this pass extends.

## 1. Objective

Let a user answer not just *"what is the number?"* but *"why should I trust this number?"* The
platform already records where every number came from, when it was fetched, whether sources agreed,
which rule resolved disagreement, and how fresh it is — this pass **assembles and exposes** that
chain consistently, rather than inventing new truth.

The deliverable is a single, consistent trust vocabulary — derived, not duplicated — surfaced
through progressive disclosure across dashboards, reconciliation, and Ask Goldy's.

## 2. Sources of Truth and Baseline

- `docs/system-context.md` — the three-layer data model (raw → bitemporal canonical → recomputable
  resolved views); the "recomputable resolution" invariant (resolved views are derived, never
  in-place edited).
- `docs/architecture/current-state.md` — package dependency table and `ArchitectureBoundariesTest`
  rules; the semantic query interfaces and authorization model.
- `docs/metrics/catalog.md` — the metric catalogue, `MetricProvenance` fields, "display + note"
  missing-data semantics.
- `docs/superpowers/specs/2026-10-07-ask-goldys-analytical-expansion-design.md` — sub-project A; the
  `MetricProvenance` seam, `ConnectorHealthQuery`, and per-metric authorization this pass extends.

**Baseline (merged, in `origin/main` + the sub-project A branch):**

- `reconciliation` already records everything the trust model needs, append-only:
  - `ResolvedDailySales` (`resolutionType` ∈ agreed/rule/override/conflict/missing,
    `authoritativeSource`, `hasConflict`, `resolvedAt`); `DailySalesResolver` derives those.
  - `DailySalesOverride` (`authoritativeSource`, `reason`, `actorEmail`, `recordedAt`,
    `supersededAt`); `ResolutionRule` (`strategy`, `sourcePriority`, `actorEmail`, `recordedAt`,
    `supersededAt`); `RuleAuditService` derives rule change history (created/updated/deleted).
  - `ReconciliationExceptionRow` (open exceptions); `SourceTotal`/`SourceMetric` (per-source values).
  - `ResolvedDailySalesQuery` / `ResolvedProductSalesQuery` / `ResolvedLabourQuery` /
    `ResolvedReservationQuery` / `ResolvedInventoryQuery` (the reconciled read models).
- `canonical` retains the provenance chain: bitemporal entities carry `sourceSystem`,
  `sourceRecordRef`, and `rawRecordId`.
- `semantic` (leaf): `MetricProvenance(metric, definitionVersion, range, grain, sourceDomain,
  dataFreshness, missingPeriods, calculationVersion)` — `dataFreshness` is currently a stub
  (`Instant.EPOCH`), with a `TODO(provenance)` in `DerivedMetricExecutor`; `MetricDefinition`
  carries `sourceDomain`; `MetricQueryServiceImpl` routes queries to executors.
- `semantic.ConnectorHealthQuery` → `ConnectorHealth(source, lastRunAt, status)` (sub-project A),
  implemented by `application`.
- `auth`: table-driven `PermissionService.require(role, ResourceKey, action)`; per-metric
  `requiredPermission` on each `MetricDefinition`.
- Frontend: widget renderer (`WidgetRenderer`), dashboard cards/charts/tables, saved-dashboard
  render (`GET /api/dashboards/{id}/render`), reconciliation screens, and Ask Goldy's
  (`answer-block.tsx`, `use-ask-goldys.ts`, `conversations-screen.tsx`).

## 3. Scope

### In scope

- A derived `TrustState` + `FreshnessState` model and a central `TrustQuery` that computes them.
- A provenance model: a compact `TrustSummary` on every metric result + a permission-limited
  drill-down endpoint.
- Per-domain freshness thresholds (provisional, configurable), and plumbing `dataFreshness` from the
  `Instant.EPOCH` stub to real `resolvedAt`/`recordedAt`.
- An explicit missing-data semantics enum (`ZERO`/`UNKNOWN`/`NOT_RECEIVED`/`UNRESOLVED`/
  `NOT_APPLICABLE`/`NOT_PERMITTED`).
- A shared trust component (`TrustIndicator` + `ProvenancePanel`) wired into all six surfaces:
  dashboard cards, charts, tables, reconciliation screens, Ask Goldy's answers, saved dashboards.
- Ask Goldy's trust metadata: structured trust state + staleness on tool results, so the model can
  hedge on incomplete/stale data.
- Reconciliation UX that explains what disagrees and why a result was chosen; readable rule/override
  audit history (read-only).
- Observability connection: data-quality status → responsible connector → operator diagnostics
  (permission-limited).
- Tests: the trust-state derivation matrix, provenance drill-down, freshness thresholds, and
  permission-limited provenance.

### Out of scope

- New source ingestion or new connectors.
- Accounting freshness beyond the declared threshold (no accounting data source yet).
- A frozen/provenance-snapshot ledger (the trust state is derived on demand, per §1).
- `labor.sensitive` staff-cost detail (still no per-staff cost data).
- Changing the resolution engine itself (rules/overrides already exist; this pass only exposes them).

## 4. Trust-state model

A venue-friendly enum, derived from reconciliation's `resolutionType` plus connector health. The
value follows from the source evidence, never asserted without it.

| `TrustState` | Label | Derivation |
|---|---|---|
| `VERIFIED` | "Verified" | `agreed` (2+ sources agree within tolerance) |
| `RESOLVED_BY_RULE` | "Resolved by rule" | `rule` (a standing rule chose the source) |
| `MANUALLY_OVERRIDDEN` | "Manual resolution" | `override` (manual override) |
| `SINGLE_SOURCE` | "Single source" | `missing` with exactly one source reported |
| `CONFLICTED` | "Unresolved conflict" | `conflict` with no rule/override resolving it |
| `INCOMPLETE` | "Incomplete" | some dates in the range unresolved (range aggregate) |
| `NOT_RECEIVED` | "No data" | zero sources / no resolved value |

`FreshnessState` is orthogonal (a value can be *verified* and *stale*): `FRESH`, `STALE`,
`SOURCE_FAILURE`, `UNKNOWN`, derived from each domain's last successful ingestion vs. its threshold
and from connector `FAILED` status.

**Aggregation rule (range):** the range's `TrustState` is the least-trusted state across its dates
(`VERIFIED` > `RESOLVED_BY_RULE` > `MANUALLY_OVERRIDDEN` > `SINGLE_SOURCE` > `CONFLICTED` >
`INCOMPLETE` > `NOT_RECEIVED`), and `INCOMPLETE` is used when some dates resolve and others do not.

## 5. Freshness rules (provisional, configurable)

Per-`sourceDomain` thresholds, provisional and stored in config (no code change to tune):

| Domain | Sources | Threshold |
|---|---|---|
| `resolved_daily_sales` (POS) | Lightspeed | 2 hours |
| `resolved_reservation_day` | OpenTable | 1 day |
| `resolved_labour_day` | Deputy | 1 day |
| `resolved_inventory_day` | CTB | 7 days |
| accounting | (deferred) | 30 days |

`dataFreshness` is plumbed from the resolved projection's `resolvedAt` (falling back to the
canonical `recordedAt`), replacing the `Instant.EPOCH` stub. `STALE` is set when the authoritative
source's last successful ingestion is older than the domain threshold; `SOURCE_FAILURE` when the
authoritative source's latest run `status` is `FAILED`.

## 6. Provenance model

- **`TrustSummary`** (compact, attached to every metric result): `{ state, freshness,
  authoritativeSource, resolvedAt, lastIngestionAt, threshold }`.
- **`Provenance`** (drill-down, on demand): the `TrustSummary` plus per-source values
  (`SourceTotal`/canonical rows), raw-record references (`rawRecordId`/`sourceRecordRef`), the
  resolution rule (strategy/sourcePriority/actor/recordedAt) or override
  (source/reason/actor/recordedAt), open reconciliation exceptions, per-source connector run status,
  and definition/calculation version.

Both are assembled by one `TrustQuery`, so the vocabulary cannot drift between surfaces.

## 7. Architecture

No new deployables. The `TrustQuery` follows the sub-project A `ConnectorHealthQuery` seam:

- **`semantic`** (leaf) defines `TrustState`, `FreshnessState`, `TrustSummary`, `Provenance`, and
  the `TrustQuery` interface:
  - `TrustSummary trustFor(MetricId metric, TimeRange range)`
  - `Provenance provenanceFor(MetricId metric, LocalDate date)`
- **`application`** implements `TrustQuery`, assembling from `reconciliation` (resolved read models,
  `DailySalesOverride`/`ResolutionRule` repositories, `RuleAuditService`, exception query) +
  `ingestion` (`ConnectorHealthQuery`) + `canonical` (source rows).
- **`MetricProvenance`** gains a `TrustSummary trust` field; `MetricQueryServiceImpl` enriches it
  via the injected `TrustQuery` (a `semantic` interface — no layering violation, the `application`
  impl is wired by Spring). So every metric result, dashboard widget, and Ask Goldy's tool result
  carries trust automatically.

`ArchitectureBoundariesTest` rules are unchanged: `semantic` stays a leaf, `application` implements
`semantic` interfaces, `reporting` still reaches only `semantic` + `auth`.

## 8. API / schema additions

- `MetricProvenance` gains `trust` (`TrustSummary`).
- `GET /api/provenance/{metricId}/{date}` → the full `Provenance` for one metric + date.
- `GET /api/provenance/{metricId}?from=&to=` → range-level `TrustSummary` + per-date states (for
  range drill-down).

**Permission-limited provenance:** the compact `TrustSummary` (on metric results) is already gated by
the metric's `requiredPermission`. The drill-down's per-source values and rule/override detail are
gated on the domain's read resource (e.g. `reconciliation.sales`); raw-record references and
connector-run detail are gated on `connectors` READ (operator-facing). Authorization happens before
any provenance is returned.

## 9. Missing-data semantics

An explicit `MissingDataStatus` on the metric value contract: `ZERO`, `UNKNOWN`, `NOT_RECEIVED`,
`UNRESOLVED`, `NOT_APPLICABLE`, `NOT_PERMITTED`. `null` is never silently rendered as `0`; a value
carries its `MissingDataStatus` (or is absent with the status) so the UI and Ask Goldy's can say
*why* a number is missing rather than guessing. This tightens the existing "display + note"
convention into a structured field.

## 10. UI — shared trust component (six surfaces)

- **`TrustIndicator`** — a small badge: trust label + "updated X ago" (or "stale", "no data").
- **`ProvenancePanel`** — progressive disclosure: value → indicator → provenance explanation →
  source/reconciliation detail (fetching the drill-down on demand).

One component, wired into: dashboard cards, charts, tables, the reconciliation screens, Ask Goldy's
answers (`answer-block.tsx`), and saved dashboards (render endpoint returns trust with each metric).
Normal screens stay clean; the indicator is subtle and expands on click.

## 11. Ask Goldy's integration

Tool results already carry `MetricProvenance` (sub-project A); with `TrustSummary` on it, the model
receives structured trust/freshness/staleness and can hedge, e.g. *"Sales appear down 12%, but
OpenTable hasn't refreshed since yesterday, so the covers comparison may be incomplete."* The system
prompt gains one rule: when a result's trust summary is `STALE`, `SOURCE_FAILURE`, `SINGLE_SOURCE`,
or `CONFLICTED`, the model must surface that caveat rather than present the number as settled. The
model never needs raw ingestion tables to infer this — it reads the bounded trust summary.

## 12. Reconciliation UX + audit

- Make each disagreement understandable: which sources differ, their values, why the current result
  was chosen, which rule applied, and what an override would change — in venue language, no DB
  jargon.
- Expose rule/override history read-only via the existing `RuleAuditService` and override rows
  (rule created/changed, override, override removed, projection recomputed). No new write paths and
  no sensitive operational detail to ordinary staff.

## 13. Observability connection

`metric incomplete`/`source-failure` → surface the responsible connector (failed/stale) → link to
ingestion/log detail for **authorised operators only** (`connectors`), kept separate from ordinary
staff detail.

## 14. Testing strategy

- **Trust-state derivation matrix** (unit, over the `TrustQuery` impl with stubbed/real
  reconciliation reads): agreeing sources → `VERIFIED`; single source → `SINGLE_SOURCE`; disagreement
  → `CONFLICTED`; rule → `RESOLVED_BY_RULE`; override → `MANUALLY_OVERRIDDEN`; unresolved conflict →
  `CONFLICTED`; failed connector → `SOURCE_FAILURE`; stale connector → `STALE`; missing source →
  `NOT_RECEIVED`; permission-limited → drill-down denied for a role without the domain read.
- **Provenance drill-down** (integration, Postgres): a resolved value assembles its per-source
  values, raw-record refs, rule/override, and freshness correctly.
- **Freshness thresholds**: a value inside/outside the domain threshold maps to `FRESH`/`STALE`.
- **Missing-data enum**: `null` + `NOT_RECEIVED`/`UNRESOLVED` etc. round-trips through API + UI.
- **Frontend (Vitest)**: `TrustIndicator`/`ProvenancePanel` render each state; progressive disclosure;
  Ask Goldy's hedge on a `STALE` result.

## 15. Decisions Recorded

- **Derived, not persisted:** trust state + provenance are pure functions over existing
  reconciliation/ingestion/canonical state; the audit trail is already the append-only override/rule
  rows. No new truth columns.
- **Central `TrustQuery`** (semantic interface + application impl) so the vocabulary is consistent by
  construction; `MetricProvenance` gains `TrustSummary` and every result carries trust.
- **Freshness thresholds** are provisional configurable defaults (POS 2h, reservations 1d, labour 1d,
  inventory 7d, accounting 30d), pending stakeholder numbers.
- **Permission-limited provenance**: compact summary on the metric's permission, drill-down on the
  domain read + `connectors` for operator detail.
- **All six UI surfaces** via one shared `TrustIndicator`/`ProvenancePanel`.

## 16. Known Limitations / Deferred

- Accounting freshness is declared but not measured (no data source).
- Freshness thresholds are provisional and must be tuned against real connector cadence.
- Trust state is a point-in-time derivation, not a frozen snapshot (the intended "recomposable"
  behaviour — a re-projection changes the derivation, not history).
- The `MetricProvenance.dataFreshness` plumb is per-domain best-effort; `UNKNOWN` remains possible for
  sources with no ingestion run yet.

## 17. Open Questions

- **Exact freshness thresholds** — confirm the provisional numbers with stakeholders (POS/reservations
  cadence is the highest-impact).
- **Drill-down granularity for range queries** — whether `GET /api/provenance/{metricId}?from=&to=`
  returns per-date entries or an aggregate; proposed per-date list.
- **Where the trust indicator lives on charts/tables** — per-series vs. per-widget; proposed
  per-widget (one indicator per widget, expanded to per-series in the panel).
