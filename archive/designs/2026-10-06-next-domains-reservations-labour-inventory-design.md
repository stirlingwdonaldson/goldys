# Reservations, Labour & Inventory — Resolved Semantic Domains Design

**Status:** In review
**Date:** 2026-10-06
**Scope:** Design for the next three business domains — reservations/covers, labour,
and inventory/food cost — implemented as coherent vertical slices over the existing
raw → canonical → reconciliation → resolved → semantic → delivery architecture. No
implementation; design only, staying inside the invariants in
`docs/system-context.md` and `docs/architecture/current-state.md`.

---

## 1. Objective

Extend the platform beyond sales by adding resolved semantic support for three
domains, in this order, each following the exact dependency direction already
proven by the sales domain:

```
external source → raw_record → canonical domain entity → matching/reconciliation
→ resolved projection → semantic query service → dashboard/reporting/AI
```

The binding invariant (unchanged): **no dashboard or reporting consumer derives
final business truth from raw or canonical records.** Every final business metric
flows through a resolved projection and a semantic query interface.

The secondary goal is to end the pass with a *repeatable* recipe for adding a domain,
captured as a runbook and enforced by architecture tests.

## 2. Scope and non-goals

### In scope

- **Reservations** — full slice: resolved daily/period reservation model, semantic
  queries (covers, bookings, no-shows, party size, conversion, service periods),
  reporting tools, dashboard/Reservations-page consumption.
- **Labour** — full slice: canonical labour entry, resolved daily labour model,
  semantic queries (scheduled/actual hours, labour cost, FOH/BOH %, variance,
  hours/cost per cover), granular permission resources, reporting tools.
- **Inventory** — full slice for the *invoice/purchase* path (CSV metadata + PDF
  line-item text extraction), plus designed-but-flagged models for stock movement
  and wastage whose ingestion source is not yet confirmed.
- **Ingestion surfaces** — Deputy webhook (accepts any payload, raw-only), CTB
  invoice CSV upload, CTB invoice PDF upload.
- **Cross-domain** — granular permission resources, architecture tests, matching/
  identity documentation, and a domain runbook.

### Non-goals (explicitly unchanged)

- No data warehouse, microservices, message bus, or generic analytics engine.
- No write-back to any source system, at any phase.
- No cross-source reconciliation manufactured where only one authoritative source
  exists (all three domains are single-source today).
- No invented Deputy source-details: the labour schema is domain-driven and
  provisional, and the raw webhook is kept separate from canonicalization.
- No freeform SQL or generic AI query tool; AI answers only through the fixed tool set.

## 3. Source-data reality (as of this design)

| Domain | Source | Working ingestion path | Status |
|---|---|---|---|
| Reservations | OpenTable | Operator CSV drop (exists) | Live |
| Labour | Deputy | Webhook at `deputy.swd.sh` → `POST /api/ingest/deputy`, accepts any payload | Endpoint built now; first payload expected 6am 2026-10-07; schema unconfirmed |
| Inventory — invoices | CTB | CSV export (metadata) + bulk PDF export (line items) | CSV+PDF built now |
| Inventory — stock / wastage | CTB | Stocktake/wastage export (schema unconfirmed) | Models + semantic interfaces only; ingestion deferred |

Consequence for design:

- **Labour canonicalization is NOT deferred** — we build the `CanonicalLabourEntry`
  schema now against the domain, keep it fully decoupled from the raw webhook, and
  adjust when the first Deputy payload is observed.
- **Invoice line items require text extraction** — the CSV carries only invoice
  metadata (supplier, invoice number, date, total); the PDFs carry the line items.
  Text extraction is abstracted behind a swappable port (see §7.3) so the extractor
  can be replaced without touching the line parser.

## 4. Architecture recap (binding)

Dependency direction and package roles are unchanged from
`docs/architecture/current-state.md`. The points that matter for this design:

- `semantic` is a leaf — interfaces + metric records, no platform dependencies.
- `reconciliation` implements `semantic` interfaces over resolved projections, and
  is the only place (besides provenance views in `application`) that reads canonical.
- `application` composes semantic interfaces, authorizes read use cases, and is the
  home of cross-domain derived metrics.
- `reporting` consumes `semantic` only; `api` never reads canonical/raw; `conversational`
  never reaches persistence.
- Permission is the table-driven department × seniority × resource model, enforced at
  one point (`PermissionService.require`).

## 5. Reservations

### 5.1 Canonical (unchanged)

`CanonicalReservation` (single source `OPENTABLE`) already exists and is correct.
Logical identity = `UUID.nameUUIDFromBytes("reservation:" + reservationId)`.

### 5.2 Resolved projection

New table `resolved_reservation_day`, disposable, keyed by `(trading_date,
service_period)`. It stores **raw daily counts only**; ratios (avg party size,
no-show rate, conversion) are derived in the semantic layer, not persisted:

```
trading_date date           -- the reservation's local date
service_period text         -- LUNCH | DINNER (derived)
bookings bigint             -- count of all reservations for the date
attended bigint             -- count of {SEATED, COMPLETED, WALK_IN}
covers bigint               -- sum of party_size over attended
cancelled bigint            -- count of CANCELLED
no_shows bigint             -- count of NO_SHOW
walk_ins bigint             -- count of WALK_IN
resolution_type text
authoritative_source text
has_conflict boolean
resolved_at timestamptz
PRIMARY KEY (trading_date, service_period)
```

**Definitions** (fixed, so no consumer interprets them differently):

- `bookings` = every reservation for the date, any status.
- `attended` = parties that turned up (`SEATED ∪ COMPLETED ∪ WALK_IN`).
- `covers` = `Σ party_size` over `attended`.
- `avg_party_size` = `covers / attended` (computed).
- `no_show_rate` = `no_shows / bookings` (computed).
- `booking_to_cover_conversion` = `attended / bookings` (computed).

**Service period is derived in the projector**, not stored in canonical and not baked
into the parser: `reservation_time < servicePeriodLunchCutoff (default 15:00) → LUNCH`,
else `DINNER`. The cutoff is a property (`reservations.service-period.lunch-cutoff`),
so it is configurable without a schema change. *Default 15:00 is provisional and easily
changed.*

`ReservationProjector` aggregates current canonical reservations into day × period
buckets and applies override → single-source → rule precedence (mirroring
`ProductSalesResolver`'s single-source branch).

### 5.3 Semantic interface

`ReservationMetricsQuery` (in `semantic`):

```java
List<CoversMetric>          dailyCovers(LocalDate from, LocalDate to);
Optional<ReservationSummary> summary(LocalDate date);
List<ServicePeriodCovers>   coversByServicePeriod(LocalDate from, LocalDate to);
PeriodComparison            comparePeriods(Period a, Period b);
Optional<BigDecimal>        noShowRate(LocalDate from, LocalDate to);
Optional<BigDecimal>        bookingToCoverConversion(LocalDate from, LocalDate to);
```

Metric records: `CoversMetric(date, covers, authoritativeSource, hasConflict)`,
`ReservationSummary(date, bookings, covers, cancelled, noShows, walkIns,
avgPartySize, noShowRate, bookingToCoverConversion)`, `ServicePeriodCovers(date,
period, covers)`, `PeriodComparison(...)`.

`ResolvedReservationQuery` implements it over `resolved_reservation_day`.

### 5.4 Covers stance

Covers are **single-source from OpenTable** (sum of party size). They are NOT
reconciled against Lightspeed's `Covers` metric (a downstream echo of the same party
size). This is the "do not manufacture reconciliation" ruling.

### 5.5 Manual override

`ReservationOverride` table keyed by `(trading_date, service_period)` with an
optional `overridden_covers` value + reason + actor + `recorded_at`/`superseded_at`
(bitemporal, mirroring `daily_sales_override`). The resolver prefers an override
value over the computed aggregate.

## 6. Labour

### 6.1 Canonical entity

New `CanonicalLabourEntry` (replaces the V1 `CanonicalShift` placeholder), single
source `DEPUTY`, bitemporal:

```
source_record_ref varchar   -- Deputy shift/timesheet id (provisional)
staff_ref varchar           -- staff identifier (opaque string, not a profile FK)
department varchar          -- FOH | BOH | ... (extensible)
labour_date date            -- the trading date the hours belong to
scheduled_hours numeric
actual_hours numeric
scheduled_cost numeric      -- nullable; sensitive
actual_cost numeric         -- nullable; sensitive
shift_start timestamptz     -- nullable
shift_end timestamptz       -- nullable
(+ bitemporal columns: valid_from/valid_to, recorded_at/superseded_at)
```

Logical identity = `UUID.nameUUIDFromBytes("labour:" + sourceRecordRef)`.

The field set is **domain-driven and provisional** — the user will confirm against the
first Deputy payload. The webhook stores raw bytes only and is decoupled from this
schema, so adjusting the schema later does not touch the ingestion envelope.

### 6.2 Resolved projection

New table `resolved_labour_day`, keyed by `(trading_date, department)`:

```
trading_date date
department text
scheduled_hours numeric
actual_hours numeric
scheduled_cost numeric
actual_cost numeric
variance numeric            -- scheduled - actual hours (derived)
resolution_type text
authoritative_source text
has_conflict boolean
resolved_at timestamptz
PRIMARY KEY (trading_date, department)
```

`LabourProjector` aggregates current canonical entries per day × department.

### 6.3 Semantic interface

`LabourMetricsQuery` (in `semantic`) — single-domain metrics only:

```java
List<LabourMetric> dailyLabour(LocalDate from, LocalDate to);
BigDecimal scheduledHours(LocalDate from, LocalDate to);
BigDecimal actualHours(LocalDate from, LocalDate to);
BigDecimal labourCost(LocalDate from, LocalDate to);
BigDecimal scheduledVsActualVariance(LocalDate from, LocalDate to);
```

Cross-domain labour metrics (`fohLabourCostPercent`, `bohLabourCostPercent`,
`hoursPerCover`, `labourCostPerCover`) are **not** in this interface — they require
sales or covers and live in `application` (see §9.1).

### 6.4 Permissions

Three resources, keeping wage detail out of aggregate metrics:

- `labour.hours` — scheduled/actual hours, variance, hours-per-cover (non-sensitive).
- `labour.cost` — aggregate labour cost, FOH/BOH labour-cost % (aggregate, not
  per-person).
- `labour.wages` — per-entry wage/cost data (owner-only).

Aggregate labour metrics require only `labour.cost`; nothing that renders an aggregate
requires `labour.wages`.

### 6.5 Ingestion (webhook)

`POST /api/ingest/deputy` — public, token-gated (`X-Webhook-Token` / `token` query
param, fail-closed via `DEPUTY_DROP_TOKEN`), stores the body byte-faithfully with the
incoming `content-type` and records the run. **No canonicalization in this endpoint.**
A `DeputyPayloadParser` is a thin mapper to be finalized when the first payload is
observed; it is not invoked by the webhook yet. The exact token mechanism
(header vs query param vs Deputy's own signature header) is a deployment detail to
confirm against Deputy's webhook UI.

## 7. Inventory / food cost

### 7.1 Canonical entities (observations only — no metrics)

**`CanonicalInvoice`** (single source `CTB`) — metadata from CSV:

```
source_record_ref varchar    -- invoice number
supplier_name varchar
invoice_number varchar
invoice_date date
due_date date                -- nullable
total_amount numeric         -- invoice total (metadata)
(+ bitemporal columns)
```

Logical identity = `UUID.nameUUIDFromBytes("invoice:" + invoiceNumber)`.

**`CanonicalInvoiceLine`** (single source `CTB`) — line items from PDF text:

```
source_record_ref varchar    -- invoice number + line sequence
invoice_number varchar       -- links line → invoice (the CSV↔PDF join key)
product_name_key varchar     -- normalized product name (ProductNameKey)
quantity numeric
unit_cost numeric
line_total numeric
category varchar             -- nullable; from a future product→category map
(+ bitemporal columns)
```

Logical identity = `UUID.nameUUIDFromBytes("invoice-line:" + invoiceNumber + ":" + lineSeq)`.

**`CanonicalStockCount`** (single source `CTB`) — stocktake observation (provisional,
ingestion deferred): `counted_date`, `product_name_key`, `quantity_on_hand`.

**`CanonicalWastage`** (single source `CTB`) — wastage observation (provisional,
ingestion deferred): `wastage_date`, `product_name_key`, `quantity`, `reason` (nullable).

### 7.2 Resolved projection

New table `resolved_inventory_day`, keyed by `trading_date`:

```
trading_date date
purchases numeric            -- COGS = sum of invoice line totals for the day
wastage numeric              -- sum of wastage for the day (null when no source)
stock_on_hand numeric        -- latest stock count (null when no source)
resolution_type text
authoritative_source text
has_conflict boolean
resolved_at timestamptz
PRIMARY KEY (trading_date)
```

`InventoryProjector` aggregates invoices/lines (and, when sources exist, wastage/stock)
into daily rows.

### 7.3 Invoice text extraction (modular)

A swappable port, so the extractor can be replaced without touching line parsing or
canonicalization:

- `DocumentTextExtractor` (interface) — `String extractText(byte[] document, String
  contentType)`.
- `PdfBoxTextExtractor` — first implementation (Apache PDFBox).
- `InvoiceLineTextParser` — parses extracted text into `InvoiceLine` records
  (provisional schema; finalized against a real invoice PDF).

`CtInvoiceIngestService` depends on `DocumentTextExtractor` + `InvoiceLineTextParser`,
never on PDFBox directly. The PDF is stored byte-faithfully first; extraction and
parsing are separate, replayable steps, and **no reporting calculation lives in the
parser** (it emits observations: product, qty, unit cost — never COGS or food-cost %).

### 7.4 Semantic interface

`InventoryMetricsQuery` (in `semantic`) — single-domain metrics only:

```java
List<InventoryMetric> dailyInventory(LocalDate from, LocalDate to);
BigDecimal purchases(LocalDate from, LocalDate to);      // COGS
BigDecimal wastage(LocalDate from, LocalDate to);
```

`foodCostPercent` (COGS ÷ sales) is cross-domain and lives in `application` (see §9.1).
`productCost`, `categoryCost`, and `inventoryVariance` are future extensions blocked on
a product→category reference map and stocktake data respectively (flagged in §11).

### 7.5 Separation of observations and metrics

Canonical holds **financial/stock observations** (invoice lines, stock counts,
wastage). Calculated **semantic metrics** (COGS, food-cost %, variance, product/
category cost) are computed only in the semantic/application layer. Ingestion parsers
never compute reporting figures.

## 8. Ingestion surfaces (summary)

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /api/ingest/opentable` | token | existing reservations CSV |
| `POST /api/ingest/deputy` | token | raw Deputy webhook payload (no canonicalization) |
| `POST /api/ingest/ctb-invoices` | token | invoice metadata CSV |
| `POST /api/ingest/ctb-invoices/pdf` | token | invoice PDFs (text-extracted → line items) |

Each has an in-app operator upload counterpart (multipart, OIDC + `connectors:write`),
matching the existing OpenTable upload. The connectors screen's `KNOWN_SOURCES` gains
`deputy-webhook` and `ctb-invoices`.

## 9. Cross-domain architecture

### 9.1 Cross-domain metrics live in `application`

The following metrics divide one domain by another and are computed in the
`application` layer by composing two `semantic` interfaces — they are deliberately
absent from the per-domain semantic interfaces:

- `foodCostPercent` = inventory `purchases` (COGS) ÷ sales.
- `fohLabourCostPercent` / `bohLabourCostPercent` = FOH/BOH labour cost ÷ sales.
- `hoursPerCover` = labour `actualHours` ÷ reservation `covers`.
- `labourCostPerCover` = labour `labourCost` ÷ reservation `covers`.

This keeps `semantic` a leaf and `reconciliation` single-domain. Nothing derives these
figures from canonical or raw.

### 9.2 Permissions (new resources + seeding)

New resources: `reservations.metrics`, `labour.hours`, `labour.cost`, `labour.wages`,
`inventory.cost`, `inventory.stock`. Seeded `ALL × OWNER` only (same convention as
`V6`/`V13`), so the owner sees everything end-to-end while BOH/FOH granular grants
remain deferred to the stakeholder field-to-role matrix. `labour.wages` is owner-only
by construction and never needed by an aggregate metric.

### 9.3 Architecture tests

Extend `ArchitectureBoundariesTest` with per-domain assertions:

- The three new controllers delegate to `application` services (never canonical/raw).
- The new reporting tools consume only their `semantic` interface (never canonical/
  reconciliation).
- The new semantic interfaces are implemented only in `reconciliation`.

The existing package-level rules (semantic leaf, reporting-consumes-semantic,
api-not-canonical, conversational-not-persistence) already cover the rest.

### 9.4 Matching and identity documentation

New `docs/connectors/matching-and-identity.md`, documenting per canonical entity:
logical identity, source identity, matching strategy, confidence, and manual
resolution path. All three domains are single-source; the one genuine intra-source
matching step is **invoice CSV metadata ↔ PDF line items**, joined on `invoice_number`
(exact match, high confidence; unmatched line items surface as exceptions, never
silently dropped).

## 10. Testing

Per domain, tests covering:

1. single-source data → resolved projection correct;
2. multiple matching source records (supersession) → latest wins, history preserved;
3. conflicting values (second source simulated) → unresolved/conflict flagged;
4. missing source data → "no data" not silently omitted;
5. manual override → override wins over computed value;
6. resolution-rule change → recompute reproducible, canonical history unmutated;
7. historical supersession (as-of query) → past state reflects what was known then;
8. semantic-query correctness (aggregates, service periods, cross-domain metrics);
9. permission boundaries (`labour.wages` denied to non-owner; aggregate metrics
   succeed with `labour.cost` only).

Plus: `DocumentTextExtractor` contract test (PDFBox impl), invoice line-parser
round-trip against a fixture, and Flyway migration tests.

## 11. Deliverables, gaps, and deferred decisions

### Delivered this pass

- Domains implemented: reservations/covers, labour, inventory (invoices; stock/wastage
  modelled).
- New canonical models: `CanonicalLabourEntry`, `CanonicalInvoice`,
  `CanonicalInvoiceLine`, `CanonicalStockCount`, `CanonicalWastage`.
- New resolved models: `resolved_reservation_day`, `resolved_labour_day`,
  `resolved_inventory_day` (+ projectors + override tables).
- New semantic services: `ReservationMetricsQuery`, `LabourMetricsQuery`,
  `InventoryMetricsQuery` (+ resolved implementations).
- New permission resources: `reservations.metrics`, `labour.hours`, `labour.cost`,
  `labour.wages`, `inventory.cost`, `inventory.stock`.
- Matching strategies: `matching-and-identity.md`.
- Dashboard/AI: reservation/labour/inventory reporting tools + widget specs; AI tools
  reuse the semantic services.
- Architecture tests + `docs/architecture/adding-a-domain.md` runbook.

### Remaining source-data gaps

- Deputy payload schema (webhook endpoint live; parser finalized on first payload).
- CTB stocktake/wastage export schema (models ready; ingestion deferred).
- Product→category reference map (blocks `categoryCost`).

### Architectural decisions deferred

- BOH/FOH granular permission seeding (stakeholder field-to-role matrix).
- Labour schema field list (provisional until Deputy payload observed).
- Service-period threshold default (15:00 lunch/dinner; configurable).
- PDF extractor choice (PDFBox first, swappable behind `DocumentTextExtractor`).
