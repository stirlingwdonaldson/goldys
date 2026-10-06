# Reservations, Labour & Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add resolved semantic support for three business domains — reservations/covers, labour, and inventory/food cost — as three sequential vertical slices reusing the sales-domain pattern, plus a Deputy webhook and invoice CSV/PDF ingestion.

**Architecture:** Each domain mirrors the existing sales slice: bitemporal canonical entity → projector/resolver → disposable resolved projection → semantic query interface → application service (authorization + cross-domain composition) → REST controller + reporting tool. `semantic` stays a leaf; cross-domain metrics (food-cost %, labour % of sales, hours/cost per cover) live in `application`. All three domains are single-source today, so reconciliation is a single-source resolver + manual override, not manufactured multi-source matching.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, Flyway migrations (Hibernate `validate`), PostgreSQL 16, JUnit 5 + ArchUnit + Testcontainers (backend); Apache PDFBox (new dependency, behind a swappable `DocumentTextExtractor` port); Next.js/TypeScript frontend (unchanged, consumption only).

**Spec:** `docs/superpowers/specs/2026-10-06-next-domains-reservations-labour-inventory-design.md` — the plan argues from the spec; executors read both.

## Global Constraints

- Every new domain follows `source → raw_record → canonical → reconciliation → resolved projection → semantic query → delivery`; no dashboard/reporting/AI consumer reads canonical or raw. (spec §1)
- No data warehouse, microservices, message bus, generic analytics engine, or write-back to sources. (spec §2)
- `semantic` is a leaf (no in-platform deps); `reporting` consumes `semantic` only; `api` never reads `canonical`/raw; `conversational` never reaches persistence. (spec §4)
- New permission resources: `reservations.metrics`, `labour.hours`, `labour.cost`, `labour.wages`, `inventory.cost`, `inventory.stock`. Seeded `ALL × OWNER` only. `labour.wages` is owner-only and never required by an aggregate metric. (spec §9.2)
- Cross-domain metrics (`foodCostPercent`, `fohLabourCostPercent`, `bohLabourCostPercent`, `hoursPerCover`, `labourCostPerCover`) live in `application`, never `semantic`. (spec §9.1)
- Canonical entities extend `BitemporalEntity`; resolvers/projectors mirror `DailySalesProjector`/`ProductSalesResolver`; resolved projections are disposable (safe to truncate + replay). (spec §4)
- Migrations are versioned Flyway files; next available number is **V20**. Hibernate is `validate`, so DDL must match entities exactly.
- Conventional Commits (atomic), never commit to `main`; work happens on `docs/next-domains-reservations-labour-inventory` (already created).

## Review Focus

These five inputs/failure modes are implied by the spec but no single task's happy-path test exercises them. Each is pinned by a test in the owning task named below.

1. **Unknown reservation status** — a `CanonicalReservation` with a status outside `{BOOKED, SEATED, COMPLETED, CANCELLED, NO_SHOW, WALK_IN}` must count toward `bookings` and be ignored by `attended`/`covers`/`no_shows`/`cancelled`, never throw. (Task R4)
2. **Service-period boundary** — a reservation at exactly the cutoff (`15:00`) must classify deterministically (cutoff is `>= cutoff → DINNER`, `< cutoff → LUNCH`). (Task R2)
3. **Zero denominators** — `noShowRate`/`bookingToCoverConversion`/`avgPartySize` on a date with `bookings == 0` or `attended == 0` must return `Optional.empty()`/`null`, never divide-by-zero. (Task R6)
4. **Out-of-order invoice ingest** — a PDF arriving before its CSV metadata (or a line item with no invoice row) must still persist lines and produce COGS; `purchases` is computed from line items, not invoice totals. (Task I5)
5. **Missing sensitive labour cost** — a labour entry with `actual_cost == null` must not make `labourCost()` throw or treat null as zero; `fohLabourCostPercent` returns empty when cost is unknown. (Task L6)

---

# Phase 0 — Edge: Deputy webhook (time-critical)

> The Deputy webhook fires at ~6am 2026-10-07 and must capture that first payload. This
> endpoint is raw-only and depends on nothing from the labour domain slice, so it is built
> first and independently.

### Task 0: Deputy raw webhook endpoint

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/api/DeputyIngestController.java`
- Modify: `backend/src/main/java/com/goldys/platform/config/SecurityConfig.java` (add `/api/ingest/deputy` to `permitAll` and CSRF-ignore lists)
- Test: `backend/src/test/java/com/goldys/platform/api/DeputyIngestControllerTest.java`

**Interfaces:**
- Consumes: `com.goldys.platform.ingestion.IngestionService#ingestPush(String sourceSystem, String connectorName, FetchMethod fetchMethod, String contentType, byte[] bytes, String characterEncoding, String fetcherIdentity)`; `com.goldys.platform.ingestion.FetchMethod`.
- Produces: `POST /api/ingest/deputy` accepting any `content-type`, token-gated.

- [ ] **Step 1: Write the failing test**

`DeputyIngestControllerTest` — a `@WebMvcTest` (mirror `OpenTableCsvIngestControllerTest`). Three cases:
  1. `POST /api/ingest/deputy` with `X-Webhook-Token: <token>` and a JSON body → `202 Accepted`, and `IngestionService.ingestPush("DEPUTY", "deputy-webhook", FetchMethod.API, contentType, bytes, null, "deputy-webhook")` is invoked exactly once.
  2. Wrong/missing token (with `deputy.drop-token` set) → `401`.
  3. Unset `deputy.drop-token` → every request `401` (fail closed).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.goldys.platform.api.DeputyIngestControllerTest'`
Expected: compilation FAIL — `DeputyIngestController` does not exist.

- [ ] **Step 3: Implement the controller**

Copy `OpenTableCsvIngestController.java`, change: class name, `@RequestMapping` path `/api/ingest`, method `@PostMapping("/deputy")`, `@Value("${deputy.drop-token:}")`, and call `ingestService.ingestPush("DEPUTY", "deputy-webhook", FetchMethod.API, contentType, body, null, "deputy-webhook")`. Accept `@RequestBody byte[] body` plus `@RequestHeader("Content-Type") String contentType`. The body is stored byte-faithfully with no parsing.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.goldys.platform.api.DeputyIngestControllerTest'`
Expected: PASS.

- [ ] **Step 5: Wire SecurityConfig + env**

In `SecurityConfig.java`, add `AntPathRequestMatcher.antMatcher("/api/ingest/deputy")` to both the `permitAll` list and the `csrf.ignoringRequestMatchers` list. In `.env.example`, add:
```
# ---- Deputy webhook (raw-only) ----
# Shared token guarding POST /api/ingest/deputy (fail-closed when unset).
# DEPUTY_DROP_TOKEN=
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/api/DeputyIngestController.java \
        backend/src/main/java/com/goldys/platform/config/SecurityConfig.java \
        backend/src/test/java/com/goldys/platform/api/DeputyIngestControllerTest.java .env.example
git commit -m "feat: raw Deputy webhook ingest endpoint"
```

---

# Phase 1 — Reservations (first vertical slice)

## Phase 1 files map

All under `backend/src/main/java/com/goldys/platform/` unless noted:

| File | Responsibility |
|---|---|
| `semantic/ReservationMetricsQuery.java` | business-query interface (leaf) |
| `semantic/CoversMetric.java`, `semantic/ReservationSummary.java`, `semantic/ServicePeriodCovers.java`, `semantic/PeriodComparison.java`, `semantic/Period.java` | metric records (leaf) |
| `reconciliation/ServicePeriod.java` | lunch/dinner classification (pure) |
| `reconciliation/ResolvedReservationDay.java` | resolved projection entity |
| `reconciliation/ResolvedReservationDayRepository.java` | projection repository |
| `reconciliation/ReservationProjector.java` | canonical → projection aggregator |
| `reconciliation/ReservationProjectionListener.java` | re-project on canonical write |
| `reconciliation/ReservationOverride.java`, `ReservationOverrideRepository.java`, `ReservationOverrideService.java` | manual override |
| `reconciliation/ResolvedReservationQuery.java` | implements `ReservationMetricsQuery` |
| `application/ReservationReportingService.java` | authorization + response mapping |
| `api/ReservationController.java` | REST delivery |
| `reporting/GetReservationSummaryTool.java`, `reporting/GetReservationSummaryInput.java` | AI tool |
| `reporting/ToolId.java` (modify) | add `GET_RESERVATION_SUMMARY` |
| `canonical/CanonicalReservationQuery.java` | query facade over existing `CanonicalReservation` |

## Phase 1 migrations

`backend/src/main/resources/db/migration/V20__reservation_read_model.sql`:

```sql
-- Reservation read model: disposable resolved daily aggregates + manual overrides.
-- Reconstructed from canonical_reservation by ReservationProjector. Never a source of truth.

CREATE TABLE resolved_reservation_day (
    trading_date date NOT NULL,
    service_period text NOT NULL,
    bookings bigint NOT NULL,
    attended bigint NOT NULL,
    covers bigint NOT NULL,
    cancelled bigint NOT NULL,
    no_shows bigint NOT NULL,
    walk_ins bigint NOT NULL,
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date, service_period)
);

CREATE TABLE reservation_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    service_period text NOT NULL,
    overridden_covers bigint,
    reason text,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_reservation_override_current
    ON reservation_override (trading_date, service_period) WHERE superseded_at IS NULL;
```

### Task R1: Migration + entity + repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__reservation_read_model.sql` (SQL above)
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedReservationDay.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedReservationDayRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ReservationOverride.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ReservationOverrideRepository.java`
- Test: `backend/src/test/java/com/goldys/platform/DatabaseMigrationTest.java` (modify if it asserts a table allowlist — confirm, else no change)

**Interfaces:**
- Produces: `ResolvedReservationDay` package-private entity with getters `tradingDate()`, `servicePeriod()`, `bookings()`, `attended()`, `covers()`, `cancelled()`, `noShows()`, `walkIns()`, `resolutionType()`, `authoritativeSource()`, `hasConflict()`, `resolvedAt()`; `ResolvedReservationDayRepository` (package-private, `extends JpaRepository<ResolvedReservationDay, ...>`) with a composite id via `@IdClass` or an `@EmbeddedId`. Use a single `@EmbeddedId ReservationDayId(LocalDate tradingDate, String servicePeriod)`.
- Produces: `ReservationOverride` package-private entity (columns `trading_date`, `service_period`, `overridden_covers` nullable `Long`, `reason`, `actor_email`, `recorded_at`, `superseded_at`; `create(...)` + `supersede(Instant)` mirroring `DailySalesOverride`); `ReservationOverrideRepository` (package-private) with `Optional<ReservationOverride> findCurrent(LocalDate date, String servicePeriod)`, `@Lock(PESSIMISTIC_WRITE) Optional<ReservationOverride> lockCurrent(LocalDate date, String servicePeriod)`, and `findAllCurrent()`.

- [ ] **Step 1: Write migration and run it**

Add `V20__reservation_read_model.sql`. Run `./gradlew test --tests 'com.goldys.platform.DatabaseMigrationTest'` and confirm it still passes (it validates migrations apply cleanly against a Testcontainers Postgres).

- [ ] **Step 2: Write the entities + repositories**

`ResolvedReservationDay` mirrors `ResolvedDailySales`: `@Entity @Table(name="resolved_reservation_day")`, `@EmbeddedId ReservationDayId`, columns matching the SQL with `@Column(name=...)`. `ResolvedReservationDayRepository extends JpaRepository<ResolvedReservationDay, ReservationDayId>` with `List<ResolvedReservationDay> findByTradingDateBetweenOrderByTradingDateAscServicePeriodAsc(LocalDate from, LocalDate to)`, `long countByHasConflictTrue()`, and a `deleteAllInBatch()` (inherited).

`ReservationOverride` + `ReservationOverrideRepository` mirror `DailySalesOverride` + `DailySalesOverrideRepository` (see the `daily_sales_override` table in `V5`), with the key columns `(trading_date, service_period)` and the value column `overridden_covers`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V20__reservation_read_model.sql \
        backend/src/main/java/com/goldys/platform/reconciliation/ResolvedReservationDay.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ResolvedReservationDayRepository.java
git commit -m "feat: reservation resolved read-model table and entity"
```

### Task R2: Service-period classification (pure)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ServicePeriod.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ServicePeriodTest.java`

**Interfaces:**
- Produces: `ServicePeriod.classify(Instant reservationAt, ZoneId zone, LocalTime lunchCutoff)` returning the string `"LUNCH"` or `"DINNER"`; `ServicePeriod.LUNCH`/`ServicePeriod.DINNER` string constants.

- [ ] **Step 1: Write the failing test**

```java
// ServicePeriodTest
LocalTime cutoff = LocalTime.of(15, 0);
ZoneId sydney = ZoneId.of("Australia/Sydney");
// 14:59 Sydney → LUNCH
assert classify(at("2026-09-20T04:59:00Z"), sydney, cutoff).equals("LUNCH");
// 15:00 Sydney → DINNER (boundary: >= cutoff is DINNER)
assert classify(at("2026-09-20T05:00:00Z"), sydney, cutoff).equals("DINNER");
// 23:00 Sydney → DINNER
assert classify(at("2026-09-20T13:00:00Z"), sydney, cutoff).equals("DINNER");
```

Note: `2026-09-20T05:00:00Z` is 15:00 AEST (UTC+10). Use `Instant.parse(...)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.goldys.platform.reconciliation.ServicePeriodTest'`
Expected: FAIL — `ServicePeriod` not found.

- [ ] **Step 3: Implement**

```java
public final class ServicePeriod {
  public static final String LUNCH = "LUNCH";
  public static final String DINNER = "DINNER";
  private ServicePeriod() {}
  public static String classify(Instant reservationAt, ZoneId zone, LocalTime lunchCutoff) {
    LocalTime local = reservationAt.atZone(zone).toLocalTime();
    return local.isBefore(lunchCutoff) ? LUNCH : DINNER;
  }
}
```

- [ ] **Step 4: Run test to verify it passes** — `./gradlew test --tests '...ServicePeriodTest'` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: service-period classification for reservations"`

### Task R3: Canonical reservation query facade

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ReservationView.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationRepository.java` (add a `findAllCurrent` already exists; add date-range query if missing)

**Interfaces:**
- Produces: `CanonicalReservationQuery.currentReservationsForDates(Collection<LocalDate> dates)` returning `List<ReservationView>`; `ReservationView(LocalDate tradingDate, String sourceSystem, Instant reservationAt, int partySize, String status)`.

The `tradingDate` is the reservation's local date in the venue zone — the query facade injects `@Value("${opentable.timezone:Australia/Sydney}") ZoneId zone` and derives `tradingDate = reservationAt.atZone(zone).toLocalDate()`. This matters because `CanonicalReservation` stores an `Instant`, not a `LocalDate`, so the projector needs the date carried through explicitly.

- [ ] **Step 1: Write the failing test**

`CanonicalReservationIntegrationTest` already exists; extend it with a test that records a reservation, then `canonicalReservationQuery.currentReservationsForDates(...)` returns a `ReservationView` with the right `status`/`partySize` and a `tradingDate` matching the reservation's local date. (Package-private access is fine — the test is in the `canonical` package.)

- [ ] **Step 2: Implement**

Add a query to `CanonicalReservationRepository` (or reuse `findAllCurrent()` and filter in the facade — pub scale). `CanonicalReservationQuery` injects the `ZoneId`, maps `CanonicalReservation` → `ReservationView`, and filters/derives `tradingDate`.

- [ ] **Step 3: Commit** — `git commit -m "feat: reservation query facade for the projector"`

### Task R4: Reservation projector + listener

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ReservationProjector.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ReservationProjectionListener.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/StartupProjectionSeeder.java` (add `reservationProjector.recomputeAll()`)
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ReservationProjectorIntegrationTest.java`

**Interfaces:**
- Consumes: `CanonicalReservationQuery.currentReservationsForDates`, `ReservationOverrideRepository.findCurrent(...)`, `ServicePeriod.classify`.
- Produces: `ReservationProjector.recompute(LocalDate... dates)` and `recomputeAll()`.

- [ ] **Step 1: Write the failing test** (covers Review Focus #1)

`ReservationProjectorIntegrationTest` (Testcontainers-backed, mirror `DailySalesProjectorIntegrationTest`):
1. Record two reservations on date D (one `SEATED` party 4, one `NO_SHOW` party 2) → `recompute(D)` → `resolved` row for D has `bookings=2, attended=1, covers=4, no_shows=1`.
2. **Unknown status**: record a reservation with status `"UNKNOWN_STATUS"` → `recompute(D)` → `bookings=3`, `attended`/`covers` unchanged, no exception.

- [ ] **Step 2: Implement the projector**

Mirror `DailySalesProjector.recompute(Collection<LocalDate>)`: load `ReservationView`s for the dates, classify each into `(date, servicePeriod)`, aggregate counts using the definitions from spec §5.2 (`bookings` = all; `attended` = SEATED∪COMPLETED∪WALK_IN; `covers` = Σ party_size over attended; `cancelled`/`no_shows`/`walk_ins` = counts), apply any `ReservationOverride` (overridden_covers replaces covers), and upsert `ResolvedReservationDay` rows with `resolutionType="single"`, `authoritativeSource="OPENTABLE"`, `hasConflict=false`. Only emit rows for dates that have data. Use a `Clock.systemUTC()` and `OperationalMetrics` if desired (optional — the sales projectors use it; mirror it).

`ReservationProjectionListener` mirrors `DailySalesProjectionListener`: it listens for a `ReservationRecorded` event and calls `projector.recompute(event.tradingDate())`. Create the event:

```java
// canonical/ReservationRecorded.java
public record ReservationRecorded(LocalDate tradingDate) {}
```

Publish it from `CanonicalReservationService`: inject `ApplicationEventPublisher` and the same `ZoneId` as the query facade (`@Value("${opentable.timezone:Australia/Sydney}")`), and in `record(...)` after the save, publish `new ReservationRecorded(input.reservationAt().atZone(zone).toLocalDate())`. This mirrors `CanonicalDailySalesService.record` (which publishes `DailySalesRecorded` in the same transaction), with the one difference being the `Instant` → `LocalDate` derivation.

- [ ] **Step 3: Run test to verify it passes** — `./gradlew test --tests '...ReservationProjectorIntegrationTest'` → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: reservation projector and projection listener"`

### Task R5: Semantic interface + metric records

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/ReservationMetricsQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/CoversMetric.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/ReservationSummary.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/ServicePeriodCovers.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/Period.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/PeriodComparison.java`

**Interfaces:**
- Produces (exact — later tasks depend on these):

```java
public interface ReservationMetricsQuery {
  List<CoversMetric> dailyCovers(LocalDate from, LocalDate to);
  Optional<ReservationSummary> summary(LocalDate date);
  List<ServicePeriodCovers> coversByServicePeriod(LocalDate from, LocalDate to);
  PeriodComparison comparePeriods(Period a, Period b);
  Optional<BigDecimal> noShowRate(LocalDate from, LocalDate to);
  Optional<BigDecimal> bookingToCoverConversion(LocalDate from, LocalDate to);
}
public record CoversMetric(LocalDate date, long covers, String authoritativeSource, boolean hasConflict) {}
public record ReservationSummary(LocalDate date, long bookings, long attended, long covers,
    long cancelled, long noShows, long walkIns, BigDecimal avgPartySize, BigDecimal noShowRate,
    BigDecimal bookingToCoverConversion) {}
public record ServicePeriodCovers(LocalDate date, String servicePeriod, long covers) {}
public record Period(LocalDate from, LocalDate to) {
  public Period { if (to.isBefore(from)) throw new IllegalArgumentException("to before from"); }
}
public record PeriodComparison(long coversA, long coversB, BigDecimal coversDeltaPercent) {}
```

- [ ] **Step 1: Write the files** (no logic — interfaces and records only; `semantic` must stay a leaf with zero in-platform imports beyond `java.*`).

- [ ] **Step 2: Verify the leaf rule holds** — Run `./gradlew test --tests 'com.goldys.platform.architecture.ArchitectureBoundariesTest'` → PASS (semantic still a leaf).

- [ ] **Step 3: Commit** — `git commit -m "feat: reservation semantic query interface and metric records"`

### Task R6: Resolved query (implements semantic)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedReservationQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolvedReservationQueryIntegrationTest.java`

**Interfaces:**
- Consumes: `ResolvedReservationDayRepository`.
- Produces: `@Service ResolvedReservationQuery implements ReservationMetricsQuery`.

- [ ] **Step 1: Write the failing test** (covers Review Focus #3)

`ResolvedReservationQueryIntegrationTest`: seed `resolved_reservation_day` rows directly (via repository) and assert:
1. `dailyCovers(from,to)` returns covers summed across both service periods, ordered by date.
2. `summary(date)` computes `avgPartySize = covers/attended` and `noShowRate = noShows/bookings` and `bookingToCoverConversion = attended/bookings` with `BigDecimal` scale 4.
3. **Zero denominators**: a row with `bookings=0, attended=0` → `noShowRate` returns `Optional.empty()`; `summary` returns `avgPartySize=null`.
4. `coversByServicePeriod(from,to)` returns one row per (date, period).
5. `comparePeriods(a,b)` returns `coversDeltaPercent = (coversB - coversA) / coversA` (scale 4), empty-safe when `coversA == 0`.

- [ ] **Step 2: Implement**

`ResolvedReservationQuery` maps `ResolvedReservationDay` → metric records. `summary(date)` aggregates both periods for the date and computes the three ratios with `BigDecimal` (guard zero denominators → `null`). `comparePeriods` sums `covers` over each period range.

- [ ] **Step 3: Run test to verify it passes** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: resolved reservation query"`

### Task R7: Reservation override service

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ReservationOverrideService.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ReservationOverrideServiceTest.java`

**Interfaces:**
- Consumes: `ReservationOverrideRepository` (created in R1), `PermissionService`, `ReservationProjector`.
- Produces: `ReservationOverrideService.save(UserRole actor, String actorEmail, LocalDate date, String servicePeriod, Long overriddenCovers, String reason)` → returns saved override; `Optional<Long> currentCovers(date, period)`.

- [ ] **Step 1: Write the failing test**

`ReservationOverrideServiceTest` (mock `PermissionService` + real repo via integration, or pure unit): saving an override for (date, LUNCH) sets `overridden_covers`, then the projector's `recompute` reads it and the resolved row's `covers` equals the override; saving again supersedes the first (bitemporal: `superseded_at` set).

- [ ] **Step 2: Implement**

`ReservationOverrideService.save` does `permissions.require(actor, new ResourceKey("reservations.metrics"), WRITE)`, locks current via `lockCurrent(date, period)`, supersedes, saves a new `ReservationOverride`, then `projector.recompute(date)` (mirror `DailySalesOverrideService`).

- [ ] **Step 3: Run test to verify it passes** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: reservation manual override"`

### Task R8: Application service + controller

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/application/ReservationReportingService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/ReservationController.java`
- Test: `backend/src/test/java/com/goldys/platform/application/ReservationReportingServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ReservationControllerTest.java`

**Interfaces:**
- Consumes: `ReservationMetricsQuery`, `PermissionService`.
- Produces: `ReservationController` with `GET /api/reservations/summary?date=...` and `GET /api/reservations/covers?from=...&to=...`.

- [ ] **Step 1: Write the failing tests**

`ReservationReportingServiceTest` (mock `ReservationMetricsQuery` + `PermissionService`): `summary(role, date)` requires `READ` on `reservations.metrics` and maps `ReservationSummary` to a JSON-friendly record; missing permission → `AccessDeniedException`.
`ReservationControllerTest` (`@WebMvcTest`): endpoints delegate and return 200 with the mapped payload.

- [ ] **Step 2: Implement**

`ReservationReportingService` mirrors `SalesReportingService`: `RESOURCE = new ResourceKey("reservations.metrics")`, `summary(UserRole, LocalDate)` and `dailyCovers(UserRole, from, to)` each `permissions.require(..., READ)` then read `ReservationMetricsQuery`. `ReservationController` mirrors `SalesController` (`@RequestMapping("/api/reservations")`, `@AuthenticationPrincipal AccountUserDetails`, `CurrentUserService.roleOf`).

- [ ] **Step 3: Run tests** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: reservation reporting service and REST controller"`

### Task R9: Reporting tool

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (add `GET_RESERVATION_SUMMARY`)
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetReservationSummaryTool.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetReservationSummaryInput.java`
- Test: `backend/src/test/java/com/goldys/platform/reporting/GetReservationSummaryToolTest.java`

**Interfaces:**
- Consumes: `ReservationMetricsQuery`.
- Produces: `get_reservation_summary` tool returning a `StatWidgetSpec` (or `TableWidgetSpec`) with the day's `ReservationSummary`; `GetReservationSummaryInput(LocalDate date) implements ToolInput` with a `toMap()`.

- [ ] **Step 1: Write the failing test**

`GetReservationSummaryToolTest`: `execute(new GetReservationSummaryInput(date), role)` returns a `ToolResult` whose widget carries covers/bookings/no-shows; unresolved summary → a notice string. Mirror `GetSalesByPeriodToolTest`.

- [ ] **Step 2: Implement**

Add `GET_RESERVATION_SUMMARY` to `ToolId`. `GetReservationSummaryTool implements ReportingTool` — `id()`, `name()="get_reservation_summary"`, `description()`, `inputType()`, `execute()` building a `StatWidgetSpec` (see `widget/StatWidgetSpec.java`) with the summary metrics and a `WidgetQuery(ToolId.GET_RESERVATION_SUMMARY.name(), in.toMap())`.

- [ ] **Step 3: Run test** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: reservation summary reporting tool"`

### Task R10: Architecture test for reservations

**Files:**
- Modify: `backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java`
- Test: (the file itself)

- [ ] **Step 1: Add the assertions**

Add three `@ArchTest` rules:
```java
// Reservation controller must not read canonical.
@ArchTest static final ArchRule reservationControllerDoesNotReadCanonical =
    noClasses().that().haveFullyQualifiedName("com.goldys.platform.api.ReservationController")
        .should().dependOnClassesThat().resideInAPackage("..canonical..");
// Reservation reporting tool consumes semantic only.
@ArchTest static final ArchRule reservationToolConsumesSemanticOnly =
    noClasses().that().haveFullyQualifiedName("com.goldys.platform.reporting.GetReservationSummaryTool")
        .should().dependOnClassesThat().resideInAnyPackage("..canonical..", "..reconciliation..");
// ReservationMetricsQuery implemented only in reconciliation.
@ArchTest static final ArchRule reservationMetricsImplementedInReconciliation =
    classes().that().implement(ReservationMetricsQuery.class)
        .should().resideInAPackage("..reconciliation..");
```

- [ ] **Step 2: Run test** → PASS.

- [ ] **Step 3: Commit** — `git commit -m "test: reservation architecture boundary rules"`

### Task R11: Frontend Reservations page

**Files:**
- Modify: `frontend/app/(app)/reservations/page.tsx`
- Modify: `frontend/lib/api/client.ts` and `frontend/lib/api/types.ts` (add `getReservationSummary`)

**Interfaces:**
- Consumes: `GET /api/reservations/summary?date=...`.

- [ ] **Step 1: Add the API client method + type** — `ReservationSummary` type mirroring the backend record; `getReservationSummary(date: string)`.

- [ ] **Step 2: Replace the placeholder** — the page fetches today's summary and renders bookings/covers/no-shows; keep the `AwaitingData` fallback for the no-data state. Follow the `sales/page.tsx` + `use-api-data.ts` pattern.

- [ ] **Step 3: Run frontend checks** — `cd frontend && bun run lint && bun run test` → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: wire reservations page to resolved summary"`

---

# Phase 2 — Labour

> Same slice as reservations. The Deputy webhook (Task 0) already stores raw; this phase
> adds the canonical model (built now, domain-driven), resolved projection, semantic
> interface, application service, controller, reporting tool, and the three granular
> permission resources. Cross-domain labour metrics (`hoursPerCover`, `labourCostPerCover`,
> `foh/bohLabourCostPercent`) land in the application service.

### Task L1: Labour migration + canonical entity

**Files:**
- Create: `backend/src/main/resources/db/migration/V21__labour.sql`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalLabourEntry.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/LabourInput.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalLabourEntryRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalLabourEntryService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalLabourIngest.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalLabourQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/LabourView.java`

**Interfaces:**
- Produces: `CanonicalLabourIngest.record(LabourInput)`; `CanonicalLabourQuery.currentLabourForDates(Collection<LocalDate>)` → `List<LabourView>`; `LabourInput(String sourceSystem, String sourceRecordRef, String staffRef, String department, LocalDate labourDate, BigDecimal scheduledHours, BigDecimal actualHours, BigDecimal scheduledCost, BigDecimal actualCost, Instant shiftStart, Instant shiftEnd, UUID rawRecordId)`; `LabourView(...)` (same minus rawRecordId).

**Migration** `V21__labour.sql`:

```sql
CREATE TABLE canonical_labour_entry (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    staff_ref varchar(255),
    department varchar(64) NOT NULL,
    labour_date date NOT NULL,
    scheduled_hours numeric(10,2) NOT NULL,
    actual_hours numeric(10,2) NOT NULL,
    scheduled_cost numeric(14,4),
    actual_cost numeric(14,4),
    shift_start timestamp(6) with time zone,
    shift_end timestamp(6) with time zone,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_labour_current_source_fact
    ON canonical_labour_entry (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE resolved_labour_day (
    trading_date date NOT NULL,
    department text NOT NULL,
    scheduled_hours numeric(14,4),
    actual_hours numeric(14,4),
    scheduled_cost numeric(14,4),
    actual_cost numeric(14,4),
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date, department)
);

CREATE TABLE labour_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    department text NOT NULL,
    overridden_actual_hours numeric(14,4),
    reason text,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_labour_override_current
    ON labour_override (trading_date, department) WHERE superseded_at IS NULL;
```

- [ ] **Step 1: Write migration + entity + input + service + ingest + query + view**, mirroring `CanonicalReservation.java` / `CanonicalDailySalesService.java` / `CanonicalDailySalesQuery.java` exactly. Logical identity = `UUID.nameUUIDFromBytes("labour:" + sourceRecordRef)`. `sameFact(LabourInput)` compares the fact fields. Lock via `lockCurrentSourceFact(source, ref)`.

  Publish a `LabourRecorded(LocalDate labourDate)` event from `CanonicalLabourEntryService.record` (mirror `CanonicalDailySalesService`, which publishes `DailySalesRecorded` in-transaction). `labour_date` is already a `LocalDate`, so no zone derivation is needed.

- [ ] **Step 2: Write an integration test** `CanonicalLabourIntegrationTest` (mirror `CanonicalReservationIntegrationTest`): record → supersede → history preserved; `sameFact` no-op on duplicate.

- [ ] **Step 3: Run tests** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: labour canonical entity and read model tables"`

### Task L2: Labour projector + listener + resolved entity

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedLabourDay.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedLabourDayRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/LabourOverride.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/LabourOverrideRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/LabourProjector.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/LabourProjectionListener.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/StartupProjectionSeeder.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/LabourProjectorIntegrationTest.java`

**Interfaces:**
- Produces: `ResolvedLabourDay` + `ResolvedLabourDayRepository` (composite id `(tradingDate, department)`); `LabourOverride` + `LabourOverrideRepository` (key `(trading_date, department)`, value `overridden_actual_hours`, mirroring `ReservationOverride`); `LabourProjector.recompute(LocalDate...)`, `recomputeAll()`.

- [ ] **Step 1: Write the failing test** — aggregate two labour entries for (date, FOH) → `resolved_labour_day` row sums hours/cost; an entry with `actual_cost == null` → `actual_cost` in projection is null (not zero).

- [ ] **Step 2: Implement** — mirror `DailySalesProjector`/`ReservationProjector`; group by `(labourDate, department)`, sum scheduled/actual hours and cost (null-propagating: if any entry has null cost, the day's cost is null), `resolutionType="single"`, `authoritativeSource="DEPUTY"`.

- [ ] **Step 3: Run tests** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: labour projector and resolved entity"`

### Task L3: Labour semantic interface + records

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/LabourMetricsQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/LabourMetric.java`

**Interfaces:**
- Produces:

```java
public interface LabourMetricsQuery {
  List<LabourMetric> dailyLabour(LocalDate from, LocalDate to);
  BigDecimal scheduledHours(LocalDate from, LocalDate to);
  BigDecimal actualHours(LocalDate from, LocalDate to);
  BigDecimal labourCost(LocalDate from, LocalDate to);
  BigDecimal scheduledVsActualVariance(LocalDate from, LocalDate to);
}
public record LabourMetric(LocalDate date, String department, BigDecimal scheduledHours,
    BigDecimal actualHours, BigDecimal scheduledCost, BigDecimal actualCost,
    String authoritativeSource, boolean hasConflict) {}
```

- [ ] **Step 1: Write the files** (leaf).

- [ ] **Step 2: Commit** — `git commit -m "feat: labour semantic query interface"`

### Task L4: Resolved labour query

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedLabourQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolvedLabourQueryIntegrationTest.java`

**Interfaces:**
- Produces: `@Service ResolvedLabourQuery implements LabourMetricsQuery`.

- [ ] **Step 1: Write test** — sum across departments; `scheduledVsActualVariance = scheduled - actual`; null-cost handling.
- [ ] **Step 2: Implement** — mirror `ResolvedDailySalesQuery` / `ResolvedProductSalesQuery`.
- [ ] **Step 3: Commit** — `git commit -m "feat: resolved labour query"`

### Task L5: Labour override service

**Files:**
- Create: `reconciliation/LabourOverrideService.java`
- Test: `LabourOverrideServiceTest.java`

Mirror Task R7, but `ResourceKey("labour.hours")`, override field `overridden_actual_hours`, key `(trading_date, department)` (entity/repository created in L2).

- [ ] **Step 1–3:** test → implement → pass.
- [ ] **Step 4: Commit** — `git commit -m "feat: labour manual override"`

### Task L6: Labour application service + controller + cross-domain metrics

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/application/LabourReportingService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/LabourController.java`
- Test: `backend/src/test/java/com/goldys/platform/application/LabourReportingServiceTest.java`

**Interfaces:**
- Consumes: `LabourMetricsQuery`, `ReservationMetricsQuery` (for per-cover), `SalesMetricsQuery` (for % of sales), `PermissionService`.
- Produces: `GET /api/labour/summary?from=...&to=...` returning scheduled/actual hours, labour cost, variance, `hoursPerCover`, `labourCostPerCover`, `fohLabourCostPercent`, `bohLabourCostPercent`.

- [ ] **Step 1: Write the failing test** (covers Review Focus #5)

`LabourReportingServiceTest`: mock the three queries.
1. `summary` requires `READ` on `labour.cost` (for cost-bearing fields) and `labour.hours` (for hours fields) — assert both `require` calls happen.
2. `labourCostPerCover = labourCost / Σ covers`; with `covers == 0` → null.
3. **Missing cost**: `labourCost() == null` → `fohLabourCostPercent` returns `Optional.empty()`, not zero/throw.
4. `fohLabourCostPercent` = FOH `actual_cost` / `Σ grossSales`; `bohLabourCostPercent` likewise.

- [ ] **Step 2: Implement**

`LabourReportingService`:
- `RESOURCE_HOURS = new ResourceKey("labour.hours")`, `RESOURCE_COST = new ResourceKey("labour.cost")`.
- `summary(UserRole role, LocalDate from, LocalDate to)`: `require(role, RESOURCE_HOURS, READ)` then `require(role, RESOURCE_COST, READ)`; compute fields from `LabourMetricsQuery` and the two cross-domain denominators (`ReservationMetricsQuery.dailyCovers(...)` summed; `SalesMetricsQuery.dailySales(...)` summed). All ratios guarded against zero/null denominators → `null`.

- [ ] **Step 3: Run tests** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: labour reporting service with cross-domain metrics"`

### Task L7: Labour reporting tool

**Files:**
- Modify: `reporting/ToolId.java` (add `GET_LABOUR_COST`)
- Create: `reporting/GetLabourCostTool.java`, `reporting/GetLabourCostInput.java`
- Test: `GetLabourCostToolTest.java`

Mirror Task R9; tool `get_labour_cost` over `(from, to)` → `StatWidgetSpec` or `TableWidgetSpec` showing scheduled/actual hours, labour cost, variance, and per-cover metrics. Input `GetLabourCostInput(LocalDate startDate, LocalDate endDate)`.

- [ ] **Step 1–3:** test → implement → pass.
- [ ] **Step 4: Commit** — `git commit -m "feat: labour cost reporting tool"`

### Task L8: Labour architecture test

Mirror Task R10 for `LabourController` and `GetLabourCostTool` + `LabourMetricsQuery implemented in reconciliation`.

- [ ] **Step 1–3:** add rules → pass → commit (`test: labour architecture boundary rules`).

---

# Phase 3 — Inventory / food cost

> Invoices (CSV metadata + PDF line items) are real this pass. Stock/wastage are modelled
> (canonical tables + semantic interface) but ingestion is deferred.

### Task I1: Inventory migration + canonical entities

**Files:**
- Create: `backend/src/main/resources/db/migration/V22__inventory.sql`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoice.java`, `InvoiceInput.java`, `CanonicalInvoiceRepository.java`, `CanonicalInvoiceService.java`, `CanonicalInvoiceIngest.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLine.java`, `InvoiceLineInput.java`, `CanonicalInvoiceLineRepository.java`, `CanonicalInvoiceLineService.java`, `CanonicalInvoiceLineIngest.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalStockCount.java`, `CanonicalWastage.java` (models + repositories only, no ingest/service)
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInventoryQuery.java`, `InvoiceLineView.java`

**Migration** `V22__inventory.sql`:

```sql
CREATE TABLE canonical_invoice (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    supplier_name varchar(255),
    invoice_number varchar(255) NOT NULL,
    invoice_date date NOT NULL,
    due_date date,
    total_amount numeric(14,4),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_invoice_current_source_fact
    ON canonical_invoice (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE canonical_invoice_line (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    invoice_number varchar(255) NOT NULL,
    product_name_key varchar(512) NOT NULL,
    quantity numeric(14,4) NOT NULL,
    unit_cost numeric(14,4) NOT NULL,
    line_total numeric(14,4) NOT NULL,
    category varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_invoice_line_current_source_fact
    ON canonical_invoice_line (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE canonical_stock_count (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    counted_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    quantity_on_hand numeric(14,4) NOT NULL,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE TABLE canonical_wastage (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    wastage_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    quantity numeric(14,4) NOT NULL,
    reason varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE TABLE resolved_inventory_day (
    trading_date date PRIMARY KEY,
    purchases numeric(14,4),
    wastage numeric(14,4),
    stock_on_hand numeric(14,4),
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE inventory_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    overridden_purchases numeric(14,4),
    reason text,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_inventory_override_current
    ON inventory_override (trading_date) WHERE superseded_at IS NULL;
```

- [ ] **Step 1: Write migration + entities + services + ingests + query**, mirroring the labour task. Logical identities: invoice = `"invoice:" + invoiceNumber`; line = `"invoice-line:" + invoiceNumber + ":" + sourceRecordRef`. `CanonicalInventoryQuery.currentInvoiceLinesForDates(Collection<LocalDate>)` → `List<InvoiceLineView>` (`InvoiceLineView(LocalDate invoiceDate, String invoiceNumber, String productNameKey, BigDecimal quantity, BigDecimal unitCost, BigDecimal lineTotal)`).

  Publish an `InvoiceLineRecorded(LocalDate invoiceDate)` event from `CanonicalInvoiceLineService.record` (mirror `CanonicalDailySalesService`). `invoice_date` is a `LocalDate`, so no zone derivation is needed.

- [ ] **Step 2: Write `CanonicalInvoiceIntegrationTest`** (record → supersede → history).

- [ ] **Step 3: Run tests** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: inventory canonical entities and read model tables"`

### Task I2: DocumentTextExtractor port + PDFBox impl (modular)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/ingestion/port/DocumentTextExtractor.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/PdfBoxTextExtractor.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/PdfBoxTextExtractorTest.java`

**Interfaces:**
- Produces: `public interface DocumentTextExtractor { String extractText(byte[] document, String contentType); }` (in `ingestion.port` so it's swappable at the boundary); `PdfBoxTextExtractor implements DocumentTextExtractor` (uses `org.apache.pdfbox.text.PDFTextStripper`).

- [ ] **Step 1: Add PDFBox dependency** to `backend/build.gradle` (`implementation 'org.apache.pdfbox:pdfbox:3.0.4'`) — verify version against the repo's dependency management.

- [ ] **Step 2: Write the failing test**

`PdfBoxTextExtractorTest`: generate a tiny PDF in-test (PDFBox `PDDocument` with a single text line), run `extractText(bytes, "application/pdf")`, assert the extracted text contains the line. Also assert a non-PDF content type is handled (either delegated or an explicit `UnsupportedOperationException`/`ConnectorFetchException`).

- [ ] **Step 3: Implement** — `PdfBoxTextExtractor` extracts text with `PDFTextStripper`; wrap I/O failures in `ConnectorFetchException("CONNECTOR_FETCH_FAILED", ...)`.

- [ ] **Step 4: Run test** → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: swappable document text extraction port with PDFBox impl"`

### Task I3: Invoice CSV metadata ingest

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvParser.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/CtInvoiceIngestController.java`
- Modify: `backend/src/main/java/com/goldys/platform/config/SecurityConfig.java` (add `/api/ingest/ctb-invoices` + `/api/ingest/ctb-invoices/pdf` to permitAll + CSRF-ignore)
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvParserTest.java`

**Interfaces:**
- Produces: `POST /api/ingest/ctb-invoices` (token-gated) storing the CSV raw then `canonicalInvoiceIngest.record(...)` per row; `CtInvoiceCsvParser.parse(byte[])` → `List<InvoiceInput>`. Provisional CSV columns: `Supplier`, `Invoice Number`, `Invoice Date`, `Due Date`, `Total`.

- [ ] **Step 1: Write parser test** — a CSV with the provisional header parses to `InvoiceInput`s; a missing `Invoice Number` column → `ConnectorFetchException`.

- [ ] **Step 2: Implement** — mirror `OpenTableCsvParser` (column validation, blank→null). `CtInvoiceCsvIngestService` mirrors `OpenTableCsvIngestService` (`ingestPush("CTB", "ctb-invoices", FILE_EXPORT, "text/csv", ...)` then record). Controller mirrors `OpenTableCsvIngestController` with `@Value("${ctb.drop-token:}")`.

- [ ] **Step 3: Run tests** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: CTB invoice CSV metadata ingest"`

### Task I4: Invoice PDF line-item ingest + parser

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoiceLineTextParser.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoicePdfIngestService.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/InvoiceLineTextParserTest.java`

**Interfaces:**
- Consumes: `DocumentTextExtractor`, `CanonicalInvoiceLineIngest`.
- Produces: `InvoiceLineTextParser.parse(String extractedText)` → `List<InvoiceLineInput>`; `CtInvoicePdfIngestService.ingest(byte[] pdf)` (stores raw, extracts text, parses, records lines).

- [ ] **Step 1: Write the parser test**

`InvoiceLineTextParserTest`: a fixture text block with three lines like
```
Item          Qty    Unit     Total
Potatoes      2      12.50    25.00
Beef          1      45.00    45.00
```
parses to two `InvoiceLineInput`s with `productNameKey` = `ProductNameKey.normalize("Potatoes")`, `quantity=2`, `unitCost=12.50`, `lineTotal=25.00`. A text with no recognizable line rows → empty list (not a throw), with the raw text still stored.

- [ ] **Step 2: Implement**

`InvoiceLineTextParser` is a **provisional** line-oriented parser: split text into lines, skip header/footer noise, extract `productNameKey` via `ProductNameKey.normalize`, parse `quantity`/`unitCost`/`lineTotal` as `BigDecimal`. It emits observations only — no COGS or food-cost computation. Mark the class Javadoc "provisional; finalize against a real invoice PDF". `CtInvoicePdfIngestService.ingest(byte[])` = `ingestPush("CTB", "ctb-invoice-pdf", FILE_EXPORT, "application/pdf", pdf, null, ...)` → `extractor.extractText(pdf, "application/pdf")` → `parser.parse(text)` → `canonicalInvoiceLineIngest.record(...)` per line.

- [ ] **Step 3: Run test** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: CTB invoice PDF line-item ingestion"`

### Task I5: Inventory projector + resolved entity + query

**Files:**
- Create: `reconciliation/ResolvedInventoryDay.java`, `ResolvedInventoryDayRepository.java`
- Create: `reconciliation/InventoryOverride.java`, `InventoryOverrideRepository.java`
- Create: `reconciliation/InventoryProjector.java`, `InventoryProjectionListener.java`
- Modify: `reconciliation/StartupProjectionSeeder.java`
- Create: `semantic/InventoryMetricsQuery.java`, `semantic/InventoryMetric.java`
- Create: `reconciliation/ResolvedInventoryQuery.java`
- Test: `InventoryProjectorIntegrationTest.java`, `ResolvedInventoryQueryIntegrationTest.java`

**Interfaces:**
- Produces: `ResolvedInventoryDay` + `ResolvedInventoryDayRepository` (id `trading_date`); `InventoryOverride` + `InventoryOverrideRepository` (key `trading_date`, value `overridden_purchases`, mirroring `ReservationOverride`);

```java
public interface InventoryMetricsQuery {
  List<InventoryMetric> dailyInventory(LocalDate from, LocalDate to);
  BigDecimal purchases(LocalDate from, LocalDate to);   // COGS
  BigDecimal wastage(LocalDate from, LocalDate to);
}
public record InventoryMetric(LocalDate date, BigDecimal purchases, BigDecimal wastage,
    BigDecimal stockOnHand, String authoritativeSource, boolean hasConflict) {}
```

- [ ] **Step 1: Write the failing test** (covers Review Focus #4)

`InventoryProjectorIntegrationTest`:
1. Record invoice lines for date D (two lines, totals 70.00) with **no `CanonicalInvoice` row** → `recompute(D)` → `resolved_inventory_day.purchases = 70.00` (COGS from lines, not invoice totals).
2. Record invoice lines + a `CanonicalInvoice` whose `total_amount` differs → `purchases` still uses line totals.
3. `wastage`/`stock_on_hand` null when no source rows exist.

- [ ] **Step 2: Implement**

`InventoryProjector` groups `InvoiceLineView`s by date, sums `lineTotal` → `purchases`; sums `CanonicalWastage` (when present) → `wastage`; latest `CanonicalStockCount` → `stock_on_hand` (deferred — leave null). `resolutionType="single"`, `authoritativeSource="CTB"`. `ResolvedInventoryQuery` implements `InventoryMetricsQuery` (sum `purchases`/`wastage` over a range).

- [ ] **Step 3: Run tests** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: inventory projector, resolved entity, and query"`

### Task I6: Inventory application service + controller + food-cost %

**Files:**
- Create: `application/InventoryReportingService.java`
- Create: `api/InventoryController.java`
- Test: `InventoryReportingServiceTest.java`

**Interfaces:**
- Consumes: `InventoryMetricsQuery`, `SalesMetricsQuery`, `PermissionService`.
- Produces: `GET /api/inventory/summary?from=...&to=...` returning `purchases` (COGS), `wastage`, `foodCostPercent` (`purchases / Σ grossSales`).

- [ ] **Step 1: Write the failing test**

`InventoryReportingServiceTest`: `summary` requires `READ` on `inventory.cost`; `foodCostPercent = purchases / Σ grossSales`, `Optional.empty()` when either side is zero/null.

- [ ] **Step 2: Implement** — mirror `LabourReportingService`; `RESOURCE = new ResourceKey("inventory.cost")`.

- [ ] **Step 3: Run tests** → PASS.

- [ ] **Step 4: Commit** — `git commit -m "feat: inventory reporting service with food-cost percent"`

### Task I7: Inventory reporting tool

**Files:**
- Modify: `reporting/ToolId.java` (add `GET_FOOD_COST`)
- Create: `reporting/GetFoodCostTool.java`, `reporting/GetFoodCostInput.java`
- Test: `GetFoodCostToolTest.java`

Mirror Task L7; tool `get_food_cost` over `(from, to)` → `StatWidgetSpec` with `purchases` and `foodCostPercent`.

- [ ] **Step 1–3:** test → implement → pass.
- [ ] **Step 4: Commit** — `git commit -m "feat: food cost reporting tool"`

### Task I8: Inventory architecture test

Mirror Task R10 for `InventoryController`, `GetFoodCostTool`, `InventoryMetricsQuery`.

- [ ] **Step 1–3:** add rules → pass → commit (`test: inventory architecture boundary rules`).

### Task I9: Inventory override service

**Files:**
- Create: `reconciliation/InventoryOverrideService.java`
- Test: `InventoryOverrideServiceTest.java`

Mirror Task R7, but `ResourceKey("inventory.cost")`, override field `overridden_purchases`, key `trading_date` (entity/repository created in I5).

- [ ] **Step 1–3:** test → implement → pass.
- [ ] **Step 4: Commit** — `git commit -m "feat: inventory manual override"`

---

# Phase 4 — Cross-domain closure

### Task X1: Seed permission resources

**Files:**
- Create: `backend/src/main/resources/db/migration/V23__seed_domain_permissions.sql`

```sql
-- Seed owner access to the new domain resources, matching the V6/V13 convention (ALL x OWNER).
-- BOH/FOH granular grants stay deferred to the stakeholder field-to-role matrix.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'reservations.metrics', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'labour.hours', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'labour.cost', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'labour.wages', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'inventory.cost', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'inventory.stock', true, true);
```

- [ ] **Step 1: Write migration.**
- [ ] **Step 2: Write `PermissionBoundaryIntegrationTest`** (covers permission boundaries): an `ALL×OWNER` role can read `labour.cost` and `labour.wages`; a `FOH×MANAGER` role (with only `labour.hours` granted in-test) is denied `labour.cost` and `labour.wages`. Aggregate `summary` (Task L6) requires only `labour.hours` + `labour.cost`, never `labour.wages`.
- [ ] **Step 3: Run tests** → PASS.
- [ ] **Step 4: Commit** — `git commit -m "feat: seed domain permission resources"`

### Task X2: Matching & identity documentation

**Files:**
- Create: `docs/connectors/matching-and-identity.md`

Document, per canonical entity (reservation, labour entry, invoice, invoice line, stock count, wastage): logical identity, source identity, matching strategy, confidence, manual resolution path. All single-source; the one intra-source match is invoice CSV metadata ↔ PDF line items joined on `invoice_number` (exact, high confidence; unmatched lines surface as exceptions, never dropped).

- [ ] **Step 1: Write the doc.**
- [ ] **Step 2: Commit** — `git commit -m "docs: matching and identity strategies for new domains"`

### Task X3: Domain runbook

**Files:**
- Create: `docs/adding-a-domain.md`

A runbook capturing the slice recipe: the component checklist (canonical entity + input + ingest + service + query + view; migration; projector + listener + resolved entity + repository; semantic interface + records; resolved query; override; application service + controller; reporting tool + input + ToolId; permission resource + seed; architecture test), with the sales/reservations files named as the copy references.

- [ ] **Step 1: Write the doc.**
- [ ] **Step 2: Commit** — `git commit -m "docs: runbook for adding a business domain"`

### Task X4: Full test sweep + final architecture assertion

**Files:**
- Modify: `backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java` (only if any generic rule needs extension — expected none)

- [ ] **Step 1: Run the full backend suite**

Run: `./gradlew test`
Expected: PASS (all unit + integration + architecture + migration tests).

- [ ] **Step 2: Run the frontend suite**

Run: `cd frontend && bun run lint && bun run test`
Expected: PASS.

- [ ] **Step 3: Fix any failures** with atomic commits.

- [ ] **Step 4: Final commit** — `git commit -m "test: full-suite verification across new domains"` (if changes were needed).

---

## Self-review notes

- **Spec coverage:** reservations (R1–R11), labour (L1–L8), inventory (I1–I8), cross-domain permissions (X1), matching doc (X2), runbook (X3), architecture tests (R10/L8/I8), testing matrix (folded into each task's integration tests) all map to spec sections.
- **Deputy webhook** (Task 0) is built first and raw-only per spec §6.5; the labour canonical schema (Task L1) is built now, domain-driven and provisional per the user's direction.
- **PDF extractor** (Task I2) is behind the `DocumentTextExtractor` port so it is swappable per the user's direction.
- **Type consistency:** metric records, semantic interfaces, and repository/entity accessors are defined once in their producing task and referenced verbatim in consumers.
