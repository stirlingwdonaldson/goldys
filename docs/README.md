# Docs index

## Start here

| Doc | What it is | Authority |
|---|---|---|
| [system-context.md](system-context.md) | Architecture invariants, tech stack, three-layer data model, build-order prerequisites, per-source ingestion reality | **Source of truth for architecture.** Nothing else overrides it |
| [prd.md](prd.md) | Problem, goals, non-goals, phased requirements with acceptance criteria, open questions | Scopes and sequences the architecture; never overrides it |
| [architecture/current-state.md](architecture/current-state.md) | How the code is organised today: packages, allowed dependencies, authorization placement, widget/dashboard runtime | Describes the code; enforced by `ArchitectureBoundariesTest` |
| [PRODUCT.md](PRODUCT.md) | One-pass orientation summary of the two docs above | Non-normative. Never cite it; if it disagrees, it's stale |

When docs disagree: `system-context.md` wins on architecture, `prd.md` on scope,
and the code wins on "what exists". Fix the stale doc rather than working around it.

## By topic

### Architecture and domains

- [architecture/current-state.md](architecture/current-state.md): package map and rules.
- [architecture/adding-a-domain.md](architecture/adding-a-domain.md): recipe for a new
  resolved business domain (migration → canonical → projector → semantic → API/tool).
- [metrics/catalog.md](metrics/catalog.md): every metric's single definition, formula,
  grains, permission and missing-data behaviour.
- [contracts/](contracts/): the AI tool boundary
  ([ai-tool-boundary.md](contracts/ai-tool-boundary.md)) and the JSON schemas for widget
  specs and dashboard documents.

### Sources and connectors

- [connectors/source-access.md](connectors/source-access.md): how each source is reached
  (login flows, endpoints, exports) and the path the platform uses today.
- [connectors/matching-and-identity.md](connectors/matching-and-identity.md): per-entity
  identity and cross-source matching (PRD Requirement 7).
- [connectors/line-item-matching.md](connectors/line-item-matching.md): Lightspeed ↔ CTB
  product matching, measured on a real week.
- [connectors/opentable.md](connectors/opentable.md): GuestCenter export and CSV columns.

### Decisions

- [decisions/marketman-history-only.md](decisions/marketman-history-only.md): MarketMan is
  a one-time history pull, not a live source.
- [decisions/resolved-product-sales.md](decisions/resolved-product-sales.md): product
  sales get a resolved projection like daily sales.

Record new architecture decisions here as `decisions/<topic>.md` (context, decision,
consequences, status).

### Frontend and design

- [../DESIGN.md](../DESIGN.md): short entry point for design agents (impeccable), linking
  the two docs below and the constraints they must respect.
- [design/design-system.md](design/design-system.md): principles, screen inventory,
  interaction patterns (reconciliation, data tables, node graphs).
- [design/ui-direction.md](design/ui-direction.md): visual direction and tokens; overrides
  the design system's original tokens. Mockups in
  [design/ui-direction-mockups.html](design/ui-direction-mockups.html).
- [frontend/](frontend/audit.md): frontend architecture audit (2026-10-09) with the
  proposed target architecture, dependency decisions, migration plan and verification
  baseline. **Proposals, not yet approved.**

### Operations

- [operations/deployment.md](operations/deployment.md): dev, production, staging, accounts,
  SFTP drop, Sentry, upgrade and rollback.
- [operations/testing.md](operations/testing.md): local checks, test layout, CI.
- [operations/performance.md](operations/performance.md): performance baseline, changes
  made, and operational metrics.

### Audits

- [audits/production-data/](audits/production-data/README.md): read-only audit of the
  production database (2026-10-09): coverage, integrity, information loss, and
  prioritised remediation.

### History (point-in-time records)

These describe what was intended or true on their date. Use them for the *why*
behind a feature, not as a description of current code.

- [superpowers/](superpowers/README.md): dated design specs and implementation plans,
  one pair per feature.
- [handoffs/](handoffs/): session handoff notes for agents picking up mid-stream.

## Open questions

Tracked in [prd.md](prd.md#open-questions). Still open: the field-to-role permission
mapping and the full list of department/seniority values. Until they're answered,
permission resources are seeded for `ALL × OWNER` only.
