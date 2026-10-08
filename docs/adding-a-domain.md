# Adding a Business Domain

The recipe for adding a resolved semantic domain to Goldy's. Every domain follows the same
dependency direction — `external source → raw_record → canonical → reconciliation → resolved
projection → semantic query → application → REST / reporting tool` — and no dashboard or reporting
consumer may derive business truth from raw or canonical records. The sales domain
(`DailySales`/`ProductSales`) and the three newer domains (`Reservation`, `Labour`, `Inventory`)
are the worked examples; copy the closest one, don't invent a new shape.

## 1. Before you write code

1. **Pin the source reality.** Record the ingestion path (API/webhook/CSV/PDF) in
   `docs/connectors/source-access.md`, and the per-entity matching/identity in
   `docs/connectors/matching-and-identity.md`. If the source is single-authoritative, say so —
   don't manufacture multi-source reconciliation.
2. **Decide what is an observation vs a metric.** Canonical holds observations (raw facts).
   Derived business figures (rates, percentages, variance) are computed in the semantic or
   application layer, never in an ingestion parser.

## 2. The component checklist

For each new canonical entity, mirror the named reference files:

| Layer | Files to create | Mirror |
|---|---|---|
| Migration | `db/migration/V<n>__<domain>.sql` (canonical + resolved + override tables) | `V20__reservation_read_model.sql`, `V21__labour.sql` |
| Canonical entity | `<Domain>Input` (record), `<Domain>` (extends `BitemporalEntity`), `<Domain>Repository` (extends `BitemporalRepository`), `<Domain>Service` (record + lock + publish event), `<Domain>Ingest` (public facade), `<Domain>View` + `<Domain>Query` (read facade), `<Domain>Recorded` (event) | `CanonicalReservation*`, `CanonicalLabourEntry*` |
| Resolved entity | `Resolved<Domain>` + `Resolved<Domain>Repository` (disposable projection) | `ResolvedReservationDay`, `ResolvedLabourDay` |
| Projector | `<Domain>Projector` (`recomputeAll`/`recompute`) + `<Domain>ProjectionListener` | `ReservationProjector` |
| Semantic | `<Domain>MetricsQuery` (interface) + metric records | `ReservationMetricsQuery` |
| Resolved query | `Resolved<Domain>Query implements <Domain>MetricsQuery` | `ResolvedReservationQuery` |
| Override | `<Domain>Override` + `Repository` + `Service` (permission-gated write + re-project) | `ReservationOverrideService` |
| Application | `<Domain>ReportingService` (authorize read + cross-domain composition) | `ReservationReportingService`, `LabourReportingService` |
| API | `<Domain>Controller` (thin delivery) | `ReservationController` |
| Reporting tool | `<Domain>Tool` + `Input` + `ToolId` entry | `GetReservationSummaryTool`, `GetLabourVarianceTool` |
| Permissions | new `ResourceKey` + a `V<n>__seed_*` migration (seed `ALL × OWNER` only) | `V23__seed_domain_permissions.sql` |
| Architecture test | extend `ArchitectureBoundariesTest` (controller not canonical; tool semantic-only; interface implemented in `reconciliation`) | the `reservation*`/`labour*`/`inventory*` rules |
| Startup seed | add the projector to `StartupProjectionSeeder` | — |

## 3. Rules that keep the slice honest

- `semantic` is a **leaf** (no in-platform deps). Single-domain metrics only.
- Cross-domain metrics (`foodCostPercent`, `hoursPerCover`, …) live in **`application`**, composing
  two semantic interfaces.
- `reporting` consumes **`semantic` only**; `api` never reads `canonical`/raw.
- Canonical entities extend `BitemporalEntity`; resolved projections are disposable (safe to
  truncate + replay) and recomputed by projectors on write events + startup.
- New permission resources are seeded `ALL × OWNER` only; BOH/FOH granular grants wait on the
  stakeholder field-to-role matrix. Sensitive data (e.g. `labour.wages`) is a distinct resource
  from aggregate metrics (e.g. `labour.cost`).

## 4. The test matrix

Per domain, cover: single-source data, supersession, conflicting values (second source simulated),
missing data, manual override, resolution-rule change + historical supersession, semantic-query
correctness, and permission boundaries. See `ReservationProjectorIntegrationTest`,
`ResolvedReservationQueryIntegrationTest`, and `PermissionBoundaryIntegrationTest` for the shapes.
