# Production data audit — review guide

**Audit snapshot: 2026-10-09.** This folder contains the complete sanitised audit pack.

## Start here

1. [Executive summary](00-executive-summary.md) — verdict, measured findings and outstanding unknowns.
2. [Prioritised remediation](11-prioritised-remediation.md) — ordered implementation handoff.
3. [Architecture recommendations](10-architecture-recommendations.md) — additive direction and estimates.

## Detailed evidence and analysis

| Report | Contents |
|---|---|
| [Deployment state](01-deployment-state.md) | Running versions, database/schema, migrations and configuration |
| [Database inventory](02-database-inventory.md) | Tables, populations, keys, missingness and field types |
| [Source coverage](03-source-coverage.md) | Actual inputs, received datasets and processing boundaries |
| [Ingestion integrity](04-ingestion-integrity.md) | Failures, accounting, freshness and recoverability |
| [Information loss](05-information-loss.md) | Aggregated, dropped, defaulted and raw-only fields |
| [Relational integrity](06-relational-integrity.md) | Entity identities, collisions and tested joins |
| [Canonical and projections](07-canonical-and-projections.md) | History, reconciliation and independent SQL checks |
| [Analytical capabilities](08-analytical-capabilities.md) | Data Explorer, metrics and all twelve business questions |
| [Frontend/backend parity](09-frontend-backend-parity.md) | Database/API/screen coverage and discrepancies |
| [Reproducible checks](12-reproducible-checks.md) | Safe SQL methodology and operator follow-up |

## Supporting artefacts

- [Findings register](findings.csv): 23 findings with severity, confidence, evidence and action.
- [Source coverage](source-coverage.csv), [table inventory](table-inventory.csv),
  [field preservation](field-preservation.csv), [quality scorecard](data-quality-scorecard.csv).
- Mermaid diagrams: [current entities](current-entity-model.mmd),
  [current lineage](current-data-lineage.mmd), [proposed architecture](proposed-data-architecture.mmd).
- [Precise code reference index](code-reference-index.md): full source paths and cited lines.
- `evidence/`: sanitised captured SQL results and payload-structure/control summaries.
- `checks/`: reviewed read-only probes, analysis helpers and a local artefact validator.

The pack contains the evidence needed to review the findings without production credentials or raw private
payloads. Authenticated API data and rendered screens remain unverified; report 12 supplies a read-only
operator procedure for that remaining boundary. Production remained unchanged during the audit.
