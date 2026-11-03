# Production data audit — execution checklist

Audit date: 2026-10-09. Isolated branch: `audit/production-data-2026-10-09`.
Production checkout is `/srv/ai/projects/goldys`; reports are written only here.

- [x] Confirm access and isolate report workspace.
- [x] Record deployment, database identity, migration history and connector configuration.
- [x] Inventory live schema, populations, missingness and relationships.
- [x] Inspect sanitised raw structures and trace each source through implementation.
- [x] Reconcile canonical/resolved facts and investigate information loss.
- [x] Trace semantic/API/frontend boundaries and validate business questions.
- [x] Produce all Markdown, CSV and Mermaid deliverables with reproducible checks.
- [x] Validate completeness, evidence references and sanitisation.

Evidence classes: production fact; code behaviour; documented intention; inference; unknown.
All database sessions use `default_transaction_read_only=on`, statement and lock timeouts.
No ingestion, migrations, external source requests, application writes or production tests.

## Completion evidence

- 13 numbered reports, 5 requested CSVs, 3 Mermaid diagrams; 23 findings.
- 34 tables / 348 column definitions, native constraints/indexes; exact population snapshots.
- 145 selected non-PDF payloads independently verified: zero digest/byte-length mismatches.
- Current resolved checks: 187 sales dates / 13,185 product-days / 164 purchasing dates match canonical.
- Local validator checks CSV/JSON, complete ER table coverage, links, read-only SQL tokens and 52 source paths/line bounds.
- Sanitisation review: no non-public configured secret/token/email values found; no exported email-like values.
- Limit retained: no authenticated browser/API data response or page rendering observed; operator procedure supplied.
- Mermaid syntax structurally checked (ER table coverage and diagram source); no Mermaid renderer installed/invoked.
- Report files are maintained on the isolated audit branch; no application tests/builds run against production.
