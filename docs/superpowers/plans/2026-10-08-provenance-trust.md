# Provenance, Trust, and Data-Quality UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make data trust and provenance a first-class product feature: a derived, consistent trust model (`TrustState` + `FreshnessState`) assembled by a central `TrustQuery` from existing reconciliation/ingestion state, exposed as a compact `TrustSummary` on every metric result plus a permission-limited drill-down, a shared trust UI component across all six surfaces, and trust metadata for Ask Goldy's.

**Architecture:** New `semantic` (leaf) types and interfaces (`TrustQuery`, `ResolutionStateQuery`); `application` implements `TrustQuery` over `reconciliation` (resolution metadata) + `ingestion` (`ConnectorHealthQuery`) + `canonical` (source rows). `MetricProvenance` gains a `TrustSummary`, enriched by `MetricQueryServiceImpl`. A shared `TrustIndicator`/`ProvenancePanel` frontend component carries the trust affordance across dashboards, reconciliation, and Ask Goldy's.

**Tech Stack:** Java 25, Spring Boot 3.5, PostgreSQL 16 (Flyway), JUnit 5 + AssertJ + Mockito + Testcontainers; Next.js + TypeScript + Vitest (Bun).

**Spec:** `docs/superpowers/specs/2026-10-08-provenance-trust-design.md` — the plan argues from the spec; executors read both.

## Global Constraints

- Trust state + provenance are **derived**, never persisted as new truth columns. The audit trail is the existing append-only override/rule rows.
- `semantic` stays a leaf; `application` implements the new `semantic` interfaces (`TrustQuery`, `ResolutionStateQuery`); `reporting` still reaches only `semantic` + `auth`.
- A value is never silently rendered as `0`; `null` carries a `MissingDataStatus`.
- Freshness is per-`sourceDomain` (no single global threshold); thresholds are configurable.
- Provenance drill-down is permission-limited: compact summary on the metric's `requiredPermission`; per-source/rule/override detail on the domain read resource; raw-record/connector detail on `connectors`.
- No SQL/free-text; no new write paths to source systems.
- `MetricProvenance` must carry a `TrustSummary` on every result produced through `MetricQueryServiceImpl`.

## Review Focus

The five inputs/failure modes the spec implies but no task's tests would otherwise pin (each gets a test in the named task):

1. **A range with a mix of resolved and unresolved dates** — must map to `INCOMPLETE` (or the least-trusted date state), never `VERIFIED`, and never silently drop the unresolved dates. → Task 4 (`trustFor` aggregation).
2. **A source whose connector is `FAILED` or has no run yet** — must map to `SOURCE_FAILURE` / `UNKNOWN`, not `FRESH`, even if a resolved value exists. → Task 4 (freshness derivation).
3. **`dataFreshness` still `Instant.EPOCH`** after enrichment — must be replaced with a real `resolvedAt`/`recordedAt`; an enriched result whose freshness is still `EPOCH` is a bug. → Task 6 (enrichment overwrites the stub).
4. **Provenance drill-down for a role without the domain read** — must return an explicit denial, never a partial provenance. → Task 7 (permission gating).
5. **A `null` value rendered as `0`** — must carry `MissingDataStatus` (`NOT_RECEIVED`/`UNRESOLVED`/…) and render as missing, never zero. → Task 8 + Task 9 (enum integration + `TrustIndicator`).

---

## Task 1: Trust/provenance type surface (`semantic`, leaf)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/TrustState.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/FreshnessState.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/MissingDataStatus.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/TrustSummary.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/Provenance.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/ResolutionState.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/TrustQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/ResolutionStateQuery.java`

**Interfaces:**
- Produces:
  - `enum TrustState { VERIFIED, RESOLVED_BY_RULE, MANUALLY_OVERRIDDEN, SINGLE_SOURCE, CONFLICTED, INCOMPLETE, NOT_RECEIVED }`
  - `enum FreshnessState { FRESH, STALE, SOURCE_FAILURE, UNKNOWN }`
  - `enum MissingDataStatus { ZERO, UNKNOWN, NOT_RECEIVED, UNRESOLVED, NOT_APPLICABLE, NOT_PERMITTED }`
  - `record TrustSummary(TrustState state, FreshnessState freshness, String authoritativeSource, Instant resolvedAt, Instant lastIngestionAt, Duration threshold)`
  - `record ResolutionState(LocalDate date, String resolutionType, String authoritativeSource, Instant resolvedAt)`
  - `record SourceValue(String sourceSystem, BigDecimal value, Instant recordedAt)`
  - `record ResolutionDetail(String kind, String source, String reason, String actor, Instant at)`
  - `record Provenance(MetricId metric, LocalDate date, BigDecimal resolvedValue, TrustSummary trust, List<SourceValue> sources, ResolutionDetail resolution, List<UUID> rawRecordIds)`
  - `interface TrustQuery { TrustSummary trustFor(MetricId metric, TimeRange range); Provenance provenanceFor(MetricId metric, LocalDate date); }`
  - `interface ResolutionStateQuery { List<ResolutionState> states(MetricId metric, LocalDate from, LocalDate to); }`

- [ ] **Step 1: Write the failing test** — a compile-only `semantic` package test (`TrustTypeSurfaceTest`) that references every type above so a missing class fails compilation. (The type surface is the deliverable; no behaviour yet.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*TrustTypeSurfaceTest*'`
Expected: FAIL — `cannot find symbol: TrustState` etc.

- [ ] **Step 3: Implement the types** (records/enums/interfaces exactly as listed; import `java.time.Instant/LocalDate/Duration`, `java.math.BigDecimal`, `java.util.List`, `java.util.UUID`, and `MetricId`/`TimeRange` from `com.goldys.platform.semantic.catalog`).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic
git commit -m "feat(semantic): trust/provenance type surface (TrustState, TrustSummary, TrustQuery)"
```

---

## Task 2: `ResolutionStateQuery` — reconciliation impl

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolutionStateServiceImpl.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolutionStateQueryTest.java`

**Interfaces:**
- Consumes: `MetricId` (maps to a domain read), the five `Resolved*Query`/repositories.
- Produces: `ResolutionStateQuery.states(metric, from, to) → List<ResolutionState>`.

- [ ] **Step 1: Write the failing test** — assert `states(MetricId.SALES_GROSS, ...)` maps each `ResolvedDailySales` row to a `ResolutionState(date, resolutionType, authoritativeSource, resolvedAt)`; same for `PRODUCT_SALES_AMOUNT`/`RESERVATIONS_COVERS`/`LABOUR_COST`/`INVENTORY_PURCHASES` against their resolved projections (seeded in Testcontainers, or mocked repositories for the unit case).

- [ ] **Step 2: Run test to verify it fails** — `cannot find symbol: ResolutionStateServiceImpl`.

- [ ] **Step 3: Implement** — a `@Service` mapping `MetricId` → the domain's resolved read (a `switch` over `MetricId`: `SALES_GROSS/NET/GST` → `ResolvedDailySalesRepository`, `PRODUCT_*` → `ResolvedProductSalesRepository`, `RESERVATIONS_*` → `ResolvedReservationDayRepository`, `LABOUR_*` → `ResolvedLabourDayRepository`, `INVENTORY_*` → `ResolvedInventoryDayRepository`), projecting each row to `ResolutionState(date, resolutionType, authoritativeSource, resolvedAt)`.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/ResolutionStateServiceImpl.java backend/src/test/java/com/goldys/platform/reconciliation/ResolutionStateQueryTest.java
git commit -m "feat(reconciliation): ResolutionStateQuery exposes resolution metadata"
```

---

## Task 3: Freshness thresholds config

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/config/FreshnessProperties.java`
- Modify: `backend/src/main/resources/application.yml` (add `app.freshness.thresholds`)
- Test: `backend/src/test/java/com/goldys/platform/config/FreshnessPropertiesTest.java`

**Interfaces:**
- Produces: `FreshnessProperties.threshold(Duration)` or `thresholds()` → `Map<String, Duration>` bound from `app.freshness.thresholds` (`resolved_daily_sales: 2h`, `resolved_reservation_day: 1d`, `resolved_labour_day: 1d`, `resolved_inventory_day: 7d`, `accounting: 30d`).

- [ ] **Step 1: Write the failing test** — assert `thresholds().get("resolved_daily_sales")` equals `Duration.ofHours(2)` etc.

- [ ] **Step 2: Run test to verify it fails** — `cannot find symbol: FreshnessProperties`.

- [ ] **Step 3: Implement** — a `@ConfigurationProperties(prefix = "app.freshness")` record binding `Map<String, Duration> thresholds`, registered via `@EnableConfigurationProperties`/`@ConfigurationPropertiesScan` (mirror any existing config-properties class); add the YAML block.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/config/FreshnessProperties.java backend/src/main/resources/application.yml backend/src/test/java/com/goldys/platform/config/FreshnessPropertiesTest.java
git commit -m "feat(config): per-domain freshness thresholds"
```

---

## Task 4: `TrustService` — trust-state + freshness derivation (the core)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/application/TrustService.java`
- Test: `backend/src/test/java/com/goldys/platform/application/TrustServiceTest.java`

**Interfaces:**
- Consumes: `ResolutionStateQuery.states`, `ConnectorHealthQuery.health`, `FreshnessProperties`, `MetricCatalog.definition(metric).sourceDomain()`.
- Produces: `TrustQuery.trustFor(metric, range) → TrustSummary`.

- [ ] **Step 1: Write the failing test** — the derivation matrix (unit, mocked `ResolutionStateQuery`/`ConnectorHealthQuery`):
  - all dates `agreed` → `VERIFIED`;
  - one date `rule` → `RESOLVED_BY_RULE`;
  - one date `override` → `MANUALLY_OVERRIDDEN`;
  - one date `missing` (single source) → `SINGLE_SOURCE`;
  - one date `conflict` → `CONFLICTED`;
  - mixed resolved+missing dates → `INCOMPLETE`;
  - empty → `NOT_RECEIVED`;
  - source connector `FAILED` → freshness `SOURCE_FAILURE`;
  - lastIngestionAt older than threshold → `STALE`.

- [ ] **Step 2: Run test to verify it fails** — `cannot find symbol: TrustService`.

- [ ] **Step 3: Implement**

```java
@Service
public class TrustService implements TrustQuery {
  private final ResolutionStateQuery resolution;
  private final ConnectorHealthQuery connectors;
  private final FreshnessProperties freshness;
  private final MetricCatalog catalog;

  public TrustService(ResolutionStateQuery resolution, ConnectorHealthQuery connectors,
      FreshnessProperties freshness, MetricCatalog catalog) { /* assign */ }

  @Override
  public TrustSummary trustFor(MetricId metric, TimeRange range) {
    List<ResolutionState> states = resolution.states(metric, range.from(), range.to());
    String domain = catalog.definition(metric).sourceDomain();
    Duration threshold = freshness.thresholds().getOrDefault(domain, Duration.ofDays(1));
    TrustState state = aggregate(states, range);
    FreshnessState fresh = freshnessState(states, connectors.health(), threshold);
    String authoritative = states.isEmpty() ? null : states.get(0).authoritativeSource();
    Instant resolvedAt = states.stream().map(ResolutionState::resolvedAt).max(Comparator.naturalOrder()).orElse(null);
    Instant lastIngest = lastIngestionFor(authoritative, connectors.health());
    return new TrustSummary(state, fresh, authoritative, resolvedAt, lastIngest, threshold);
  }

  private static TrustState aggregate(List<ResolutionState> states, TimeRange range) {
    if (states.isEmpty()) return TrustState.NOT_RECEIVED;
    TrustState worst = TrustState.VERIFIED;
    for (ResolutionState s : states) worst = leastTrusted(worst, perDate(s.resolutionType()));
    long days = range.to().toEpochDay() - range.from().toEpochDay() + 1;
    return states.size() < days ? TrustState.INCOMPLETE : worst;
  }

  private static TrustState perDate(String resolutionType) {
    return switch (resolutionType) {
      case "agreed" -> TrustState.VERIFIED;
      case "rule" -> TrustState.RESOLVED_BY_RULE;
      case "override" -> TrustState.MANUALLY_OVERRIDDEN;
      case "conflict" -> TrustState.CONFLICTED;
      default -> TrustState.SINGLE_SOURCE; // "missing" with one source
    };
  }

  private static TrustState leastTrusted(TrustState a, TrustState b) {
    // VERIFIED is most trusted; NOT_RECEIVED least (ranked by enum ordinal, reversed).
    return a.ordinal() >= b.ordinal() ? a : b;
  }

  private static FreshnessState freshnessState(List<ResolutionState> states,
      List<ConnectorHealth> health, Duration threshold) {
    if (states.isEmpty()) return FreshnessState.UNKNOWN;
    String authoritative = states.get(0).authoritativeSource();
    ConnectorHealth h = health.stream()
        .filter(c -> c.source().equals(authoritative))
        .findFirst().orElse(null);
    if (h == null) return FreshnessState.UNKNOWN;
    if ("FAILED".equals(h.status())) return FreshnessState.SOURCE_FAILURE;
    Instant now = Instant.now();
    return h.lastRunAt() != null && now.isAfter(h.lastRunAt().plus(threshold))
        ? FreshnessState.STALE : FreshnessState.FRESH;
  }

  private static Instant lastIngestionFor(String source, List<ConnectorHealth> health) {
    return health.stream().filter(c -> c.source().equals(source))
        .map(ConnectorHealth::lastRunAt).findFirst().orElse(null);
  }

  // provenanceFor(...) is Task 7 (drill-down assembly).
}
```

- [ ] **Step 4: Run test to verify it passes** — PASS (the derivation matrix).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/application/TrustService.java backend/src/test/java/com/goldys/platform/application/TrustServiceTest.java
git commit -m "feat(application): TrustService derives trust state and freshness"
```

---

## Task 5: `dataFreshness` plumbing (replace the `Instant.EPOCH` stub)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricProvenance.java` (add `TrustSummary trust` field — see Task 6; for this task, only expose the real freshness source)
- Modify: the five `Resolved*Query` classes or their `toMetric` mappers to surface `resolvedAt` (where the semantic records drop it) — via `ResolutionStateQuery` (already done in Task 2), so this task is the consumption side.

**Interfaces:**
- Consumes: `ResolutionStateQuery` (Task 2).
- Produces: a real `Instant dataFreshness` (not `EPOCH`) on the enriched provenance (wired in Task 6).

- [ ] **Step 1: Write the failing test** — assert that after enrichment (Task 6) the `MetricProvenance.dataFreshness` is no longer `Instant.EPOCH` when a resolved `resolvedAt` exists. (If Task 6 not yet merged, write the test against the TrustSummary and mark it as the freshness source.)

- [ ] **Step 2: Run test to verify it fails** — `dataFreshness == EPOCH`.

- [ ] **Step 3: Implement** — in the enrichment step, overwrite `dataFreshness` from `trust.resolvedAt()` (falling back to `lastIngestionAt`). Remove the `TODO(provenance)` note.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit** (may fold into Task 6's commit if done together)

```bash
git commit -m "feat(semantic): plumb dataFreshness from resolution resolvedAt"
```

---

## Task 6: `MetricProvenance` gains `TrustSummary` + enrichment

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricProvenance.java` (add `TrustSummary trust` field)
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQueryServiceImpl.java` (inject `TrustQuery`, enrich the result's provenance)
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/MetricQueryServiceImplTest.java` (update)

**Interfaces:**
- Consumes: `TrustQuery.trustFor`.
- Produces: `MetricProvenance(..., TrustSummary trust)`; every `MetricQueryService.query(...)` result carries a non-null `trust`.

- [ ] **Step 1: Write the failing test** — mock `TrustQuery`; assert `metrics.query(query).provenance().trust()` equals the mocked summary and is non-null.

- [ ] **Step 2: Run test to verify it fails** — `MetricProvenance` has no `trust` accessor.

- [ ] **Step 3: Implement** — add `TrustSummary trust` to `MetricProvenance` (compact ctor null-coalesce to a `NOT_RECEIVED`/`UNKNOWN` default is NOT desired — require non-null; but existing executor constructions pass a placeholder, so add a convenience: executors build without `trust`, and `MetricQueryServiceImpl` re-creates the provenance with the real `trust` + real `dataFreshness` after the executor returns). Rebuild the `MetricResult` (both `TimeSeriesResult`/`RankedListResult` are records) with the enriched provenance via a small `withProvenance(...)` factory.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic/catalog
git commit -m "feat(semantic): MetricProvenance carries TrustSummary; MetricQueryService enriches"
```

---

## Task 7: Provenance drill-down endpoint + permission gating

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/application/TrustService.java` (implement `provenanceFor`)
- Create: `backend/src/main/java/com/goldys/platform/api/ProvenanceController.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ProvenanceControllerTest.java`

**Interfaces:**
- Consumes: `TrustQuery.provenanceFor`, `PermissionService`, `Canonical*Query`/override/rule repositories (for per-source values, raw refs, rule/override detail).
- Produces: `GET /api/provenance/{metricId}/{date}` → `Provenance` (403 for a role without the domain read; `connectors` for raw/connector detail).

- [ ] **Step 1: Write the failing test** — a role without `reconciliation.sales` gets `AccessDeniedException` on `provenanceFor(SALES_GROSS, date)`; an OWNER gets a `Provenance` whose `sources` lists per-source values.

- [ ] **Step 2: Run test to verify it fails** — endpoint/service not present.

- [ ] **Step 3: Implement** — `TrustService.provenanceFor` assembles: resolution state (Task 2) → `resolvedValue`; per-source values from the canonical daily-sales read (or the domain's canonical query); `ResolutionDetail` from the override/rule repository (kind=`override`/`rule`, source, reason/strategy, actor, recordedAt); `rawRecordIds` from canonical `rawRecordId`. `ProvenanceController` resolves `metricId`/`date`, requires the domain read (`reconciliation.sales` for sales metrics, etc. via `MetricCatalog.definition(metric).requiredPermission()`), and returns the `Provenance`.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/application/TrustService.java backend/src/main/java/com/goldys/platform/api/ProvenanceController.java backend/src/test/java/com/goldys/platform/api/ProvenanceControllerTest.java
git commit -m "feat(api): provenance drill-down endpoint with permission gating"
```

---

## Task 8: Missing-data enum integration

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricResult.java` (or the value-bearing records) to carry `MissingDataStatus` where the value is null
- Test: update the executor tests

**Interfaces:**
- Produces: a `MissingDataStatus` alongside `null` values (`NOT_RECEIVED` when no data, `UNRESOLVED` when a date is unresolved, `ZERO` for a true zero, `NOT_PERMITTED` when denied).

- [ ] **Step 1: Write the failing test** — assert an unresolved date's result carries `MissingDataStatus.UNRESOLVED`/`NOT_RECEIVED`, not just a null value.

- [ ] **Step 2: Run test to verify it fails** — no `MissingDataStatus` field.

- [ ] **Step 3: Implement** — thread the enum through the executors' null-value paths (mirror the existing "display + note" convention: null value + a `MissingDataStatus` describing why).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat(semantic): MissingDataStatus on null metric values"
```

---

## Task 9: Shared `TrustIndicator` + `ProvenancePanel` (frontend)

**Files:**
- Create: `frontend/components/trust/trust-indicator.tsx`
- Create: `frontend/components/trust/provenance-panel.tsx`
- Create: `frontend/lib/api/provenance.ts` (client method `getProvenance(metricId, date)`)
- Test: `frontend/components/trust/trust-indicator.test.tsx`

**Interfaces:**
- Consumes: the `TrustSummary` type (mirror the backend record in `frontend/lib/api/types.ts`), `getProvenance`.
- Produces: a badge (state + "updated X ago" / "stale" / "no data") that expands to the `ProvenancePanel` (fetching the drill-down on demand). No chain-of-thought; `null` values render as "No data" with the `MissingDataStatus`, never `0`.

- [ ] **Step 1: Write the failing test** — render each `TrustState`/`FreshnessState` label; assert a `null` value + `NOT_RECEIVED` renders "No data", not "0".

- [ ] **Step 2: Run test to verify it fails** — component not present.

- [ ] **Step 3: Implement** — the badge + expandable panel, mirroring the backend `TrustSummary` shape; a `formatAgo(Instant)` helper for "updated X ago".

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/trust frontend/lib/api/provenance.ts frontend/lib/api/types.ts
git commit -m "feat(frontend): shared trust indicator and provenance panel"
```

---

## Task 10: Wire trust into dashboard cards/charts/tables/saved dashboards

**Files:**
- Modify: `frontend/components/widgets/widget-renderer.tsx` (attach `TrustIndicator` to each widget from the metric result's `TrustSummary`)
- Modify: the dashboard render path/types to include `TrustSummary` per metric
- Test: update `widget-renderer`/dashboard tests

**Interfaces:**
- Consumes: `TrustSummary` on each rendered metric (from `GET /api/dashboards/{id}/render`), `TrustIndicator`.

- [ ] **Step 1: Write the failing test** — a rendered widget with a `STALE` metric shows the trust badge.

- [ ] **Step 2: Run test to verify it fails** — no badge.

- [ ] **Step 3: Implement** — surface `TrustSummary` from the render response per metric, render `TrustIndicator` (per-widget, expanding to per-series in the panel). Saved dashboards get it for free via the same render path.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/widgets frontend/lib/api
git commit -m "feat(frontend): trust indicator on dashboard widgets and saved dashboards"
```

---

## Task 11: Wire trust into reconciliation screens + audit history

**Files:**
- Modify the reconciliation screen(s) to render `TrustIndicator`/`ProvenancePanel` and a readable rule/override history (via the existing `RuleAuditService`/override rows exposed through a new/read-only endpoint).
- Test: update reconciliation screen tests.

**Interfaces:**
- Consumes: `TrustSummary`/`Provenance`, the rule/override audit read.

- [ ] **Step 1: Write the failing test** — a disagreement row shows "which sources differ, why chosen, which rule applied".

- [ ] **Step 2: Run test to verify it fails** — not rendered.

- [ ] **Step 3: Implement** — the understandable-disagreement view (venue language) + the read-only audit history list.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat(frontend): reconciliation trust + audit history"
```

---

## Task 12: Wire trust into Ask Goldy's answers + hedge

**Files:**
- Modify: `frontend/components/ask-goldys/answer-block.tsx` (render `TrustIndicator` per tool result from `provenance.trust`)
- Modify: `backend/src/main/resources/prompts/ask-goldys-system.txt` (add the hedge rule)
- Test: update `answer-block.test.tsx` + the prompt test

**Interfaces:**
- Consumes: `TraceEntry.provenance[].trust` (already flows from sub-project A's envelope), `TrustIndicator`.

- [ ] **Step 1: Write the failing test** — a tool result with a `STALE` trust summary renders the stale badge; the prompt contains the hedge rule ("surface stale/single-source/conflicted rather than present as settled").

- [ ] **Step 2: Run test to verify it fails** — no badge; prompt missing the rule.

- [ ] **Step 3: Implement** — render the trust badge from `provenance.trust` in the answer trace; add the hedge rule to the system prompt.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/ask-goldys backend/src/main/resources/prompts/ask-goldys-system.txt
git commit -m "feat(conversational): trust indicator on answers + hedge rule"
```

---

## Task 13: Observability connection (data-quality → connector → operator detail)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/application/ConnectorApplicationService.java` (or a small `DataQualityService`) to expose a `metric incomplete` → responsible-connector link for authorised operators
- Test: `backend/src/test/java/com/goldys/platform/application/DataQualityServiceTest.java`

**Interfaces:**
- Consumes: `ConnectorHealthQuery`, `TrustQuery` (or the missing-data status).
- Produces: a read gated on `connectors` mapping an incomplete metric to its responsible source + a link to ingestion/log detail (operator-facing).

- [ ] **Step 1: Write the failing test** — an incomplete metric maps to its source; a non-operator role is denied.

- [ ] **Step 2: Run test to verify it fails** — service not present.

- [ ] **Step 3: Implement** — the mapping + operator-only endpoint/field.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/application backend/src/test/java/com/goldys/platform/application
git commit -m "feat(application): data-quality to connector observability link"
```

---

## Task 14: Trust-state derivation + provenance integration tests

**Files:**
- Create: `backend/src/test/java/com/goldys/platform/application/TrustServiceIntegrationTest.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/TrustE2eTest.java` (optional)

**Interfaces:**
- Consumes: the full stack (Testcontainers).

- [ ] **Step 1: Write the failing test** — seed resolved daily sales in each state (agreeing sources, single source, conflict, rule, override), assert `TrustService.trustFor` derives the right `TrustState` end-to-end, and `provenanceFor` assembles per-source values + rule/override + raw refs.

- [ ] **Step 2: Run test to verify it fails** — integration not wired.

- [ ] **Step 3: Implement** — the integration test (this task is largely test; fix any wiring it surfaces).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java
git commit -m "test(application): trust-state derivation + provenance integration"
```

---

## Final Verification

After all tasks:

- [ ] Update `docs/architecture/current-state.md`: note the `TrustQuery`/`ResolutionStateQuery` seams (`semantic` interface, `application`/`reconciliation` impls) and the `MetricProvenance.trust` enrichment.
- [ ] `./gradlew test spotlessCheck build` passes (incl. `ArchitectureBoundariesTest` — `semantic` stays a leaf).
- [ ] `bun run typecheck`, `bun run lint`, `bun run build`, `bun run vitest run` pass.
- [ ] `docker compose config` validates.
