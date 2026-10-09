---
name: add-domain
description: Add a new resolved business domain to Goldy's (new canonical entity through projector, semantic query, REST endpoint and reporting tool). Use when asked to model a new kind of business data end to end, e.g. stock, wastage, recipes, or a second source for an existing domain.
---

The full recipe is `docs/architecture/adding-a-domain.md`. Read it first, then work
through it in order. This skill is the checklist and the traps.

## Before code

1. Record the source's ingestion path in `docs/connectors/source-access.md` and the
   entity's identity and matching in `docs/connectors/matching-and-identity.md`. If
   only one source reports it, say so; don't invent cross-source reconciliation.
2. Decide what is an observation (canonical) and what is a derived figure (computed
   in `semantic` or `application`, never in a parser).
3. Copy the closest worked example rather than inventing a shape: reservations
   (`CanonicalReservation*`, `ReservationProjector`, `ResolvedReservationQuery`) or
   labour (`CanonicalLabourEntry*`, `LabourProjector`, `ResolvedLabourQuery`).

## Checklist

- [ ] Migration (use the `add-migration` skill): canonical, resolved and override tables.
- [ ] Canonical: `<Domain>Input`, entity extending `BitemporalEntity`, repository
      extending `BitemporalRepository`, service, public `<Domain>Ingest` and
      `<Domain>Query` facades, `<Domain>Recorded` event.
- [ ] Resolved projection + `<Domain>Projector` + `<Domain>ProjectionListener`;
      register the projector in `StartupProjectionSeeder`.
- [ ] `semantic/<Domain>MetricsQuery` interface (semantic stays a leaf), implemented
      by `reconciliation/Resolved<Domain>Query`.
- [ ] Override entity, repository and permission-gated service that re-projects.
- [ ] `application/<Domain>ReportingService` (authorizes reads) and a thin
      `api/<Domain>Controller`.
- [ ] Metrics (use `add-metric`) and, if Ask Goldy's should answer it, a tool (use
      `add-reporting-tool`).
- [ ] New `ResourceKey` seeded for `ALL × OWNER` only, in a migration.
- [ ] Extend `ArchitectureBoundariesTest` with the domain's rules.
- [ ] Frontend calls via `frontend-api-method` (live + demo).

## Tests (per domain)

Single-source data, supersession, a conflicting second source (simulated), missing
data, manual override, rule change with history preserved, semantic-query correctness,
and permission boundaries. Shapes to copy: `ReservationProjectorIntegrationTest`,
`ResolvedReservationQueryIntegrationTest`, `PermissionBoundaryIntegrationTest`.

## Traps

- Business consumers (`api`, `reporting`, `conversational`, `dashboard`) must never
  read canonical or the raw ledger. If a controller wants canonical data, the semantic
  interface is missing a method.
- Resolved tables are disposable. Anything that only exists in a resolved row is lost
  on recompute.
- Cross-domain metrics (e.g. hours per cover) live in `application` or the derived
  metric executor, not in either domain's semantic interface.
