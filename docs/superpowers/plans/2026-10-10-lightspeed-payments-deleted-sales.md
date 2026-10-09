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

- Override service, metrics catalogue entries, Ask Goldy's tool — until a
  concrete question needs them.
- `salelines` line-item domain (incomplete source).

## Open decisions carried into implementation

- Payments `source_record_ref` (§3.2 of the spec): provisional
  `sale_number:payment_type_code:register_code:created_date` unless a payment
  id is available.
- Payment source-type codes stored as-is alongside the human name (no
  normalization on ingest).
