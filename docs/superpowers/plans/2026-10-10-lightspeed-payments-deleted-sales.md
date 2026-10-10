# Lightspeed Payments & Deleted-Sales — Implementation Plan

Date: 2026-10-10 · Based on `specs/2026-10-10-lightspeed-payments-deleted-sales-design.md`

Two new single-source (Lightspeed) domains, built to the `add-domain` /
`new-connector` recipes. Both follow the same shape; payments first (bigger,
exercises the identity decision), then deleted-sales (trivial identity).

## Phase 1 — Canonical + connector (payments)

1. Migration `V<n>__payments.sql`: `canonical_payment` (bitemporal columns +
   fact fields from spec §5), unique current index on
   `(source_system, source_record_ref) WHERE superseded_at IS NULL`.
2. Canonical slice: `PaymentInput`, `CanonicalPayment` (extends
   `BitemporalEntity`), `CanonicalPaymentRepository`, `CanonicalPaymentService`,
   `CanonicalPaymentIngest` (public facade), `PaymentView` +
   `CanonicalPaymentQuery` (read facade), `PaymentRecorded` (event).
3. Connector `connectors/lightspeed/`:
   - `LightspeedPayment` (DTO record) + `LightspeedPaymentCsvParser`
     (header-name-keyed, not positional).
   - `LightspeedPaymentIngestService` — `ingestPush(...)` then parse +
     `CanonicalPaymentIngest.record(...)`.
   - `POST /api/ingest/lightspeed-payments` in `LightspeedIngestController`
     (token-gated, fail-closed) + `SecurityConfig` permitAll + CSRF-ignore.
4. Parser unit tests from a sanitised fixture (payments sample).

## Phase 2 — Canonical + connector (deleted-sales)

Same as Phase 1 for `CanonicalDeletedSale`, `lightspeed-deleted-sales`.

## Phase 3 — Resolved + semantic + API (both)

5. `ResolvedPayment`/`ResolvedDeletedSale` + repositories; `<Domain>Projector`
   (single-source → resolution `single`) + `<Domain>ProjectionListener`;
   register in `StartupProjectionSeeder`.
6. `semantic/<Domain>MetricsQuery` + `reconciliation/Resolved<Domain>Query`.
7. `application/<Domain>ReportingService` + thin `api/<Domain>Controller`
   (read endpoint).

## Phase 4 — permissions, boundaries, docs, tests

8. `ResourceKey` additions + seed migration (`ALL × OWNER` only).
9. Extend `ArchitectureBoundariesTest`.
10. Integration tests (payload → raw_record; projection; semantic; permission
    boundary) mirroring `ReservationProjectorIntegrationTest` etc.
11. `./gradlew test spotlessCheck`; update `docs/connectors/source-access.md`
    and `docs/connectors/matching-and-identity.md`.

## Deferred (explicitly out of scope for now)

- Override services (manual correction) — deferred by decision; the day×type /
  day×category resolved grain makes a date-level override semantically awkward.
- Ask Goldy's reporting tools — not needed; the existing `GET_METRIC` /
  `RANK_DIMENSION` / `COMPARE_METRIC_PERIODS` tools cover catalogue metrics, so
  registering the metrics is sufficient.

## Status (2026-10-10)

Phases 1–4 done and committed on `feature/lightspeed-payments-deleted-sales`:
ingest (payments / deleted-sales / sale-items), resolved projections + semantic
queries + REST endpoints, permissions, architecture boundaries, and metric
catalogue entries (`payments.amount`, `payments.tip`, `deleted_sales.amount`,
`sale_items.amount`, `sale_items.quantity`). Full `./gradlew test spotlessCheck`
green. The `salelines` line-item source turned out to be complete once the
`product_salelines.product_id` join is removed (see the design spec §9).

## Open decisions carried into implementation

- Payments `source_record_ref` (§3.2 of the spec): provisional
  `sale_number:payment_type_code:register_code:created_date` unless a payment
  id is available.
- Payment source-type codes stored as-is alongside the human name (no
  normalization on ingest).
