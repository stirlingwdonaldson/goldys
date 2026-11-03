# Current documentation

Use these references for current project work. Architecture comes from
[system-context.md](system-context.md), scope from [prd.md](prd.md), and implementation
state from the code. Agent workflows and safety rules remain in [AGENTS.md](../AGENTS.md).

## Architecture and contracts

- [System context](system-context.md): architecture invariants and ingestion model.
- [PRD](prd.md): requirements, scope and open decisions.
- [Current architecture](architecture/current-state.md): packages, authorization and runtime.
- [Adding a domain](architecture/adding-a-domain.md): implementation recipe.
- [Metric catalogue](metrics/catalog.md): definitions, permissions and missing-data semantics.
- [AI tool boundary](contracts/ai-tool-boundary.md): bounded tools and allowed reads.
- [Widget schema](contracts/widget-spec.schema.json) and
  [dashboard schema](contracts/dashboard-document.schema.json): persisted/rendered contracts.

## Sources and decisions

- [Source access](connectors/source-access.md): actual access paths and source limitations.
- [Matching and identity](connectors/matching-and-identity.md): entity keys and matching rules.
- [OpenTable](connectors/opentable.md): assisted sign-in, JSON pulls and CSV fallback.
- [Line-item matching](connectors/line-item-matching.md): measured matching constraints.
- [CTB invoice gaps](connectors/ctb-invoice-export-gaps.md) and
  [sample](connectors/ctb-invoice-export-gaps.csv): dated source limitation evidence.
- [POS menu reconciliation](connectors/pos-menu-reconciliation-2026-10-13.md): dated matching evidence.
- [CTB recipe access](decisions/ctb-recipe-ajax.md),
  [MarketMan history-only](decisions/marketman-history-only.md),
  [resolved product sales](decisions/resolved-product-sales.md), and
  [TimesFM runtime](decisions/timesfm-inference-service.md): architectural decisions.

## UI and operations

- [Design entry point](../DESIGN.md), [UI direction](design/ui-direction.md),
  [design system](design/design-system.md), and [mockups](design/ui-direction-mockups.html).
- [Testing](operations/testing.md) and [deployment](operations/deployment.md).
- [Performance](operations/performance.md): recorded baseline and operational measurements.
- [Weekly reporting](operations/weekly-report-review.md): coverage and remaining accuracy reviews.
- [Lightspeed headline sales](operations/lightspeed-headline-sales.md),
  [separate feeds](operations/lightspeed-separated-feeds.md), and
  [bounded repair](operations/lightspeed-five-day-repair.md).
- [Sale-costing rollout](operations/sale-costing-rollout.md).
- [Data-correctness release](operations/data-correctness-release-2026-10-16.md):
  release-specific procedure; not a general instruction to run a repair or deployment.

## Agent workflows and history

- [Agent personas](agents.md): retained OpenCode reviews and commands.
- [Superpowers workflow](superpowers/README.md): locations for new designs and plans.
- [Historical archive](../archive/README.md): audit evidence, past designs, frontend
  proposals and handoffs. Consult it for a specific historical question, not startup context.

Update the existing reference when behaviour changes. Record new architectural decisions in
`decisions/`; keep implementation checklists out of current reference documentation.
