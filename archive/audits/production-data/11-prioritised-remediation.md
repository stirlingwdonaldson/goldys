# 11 — Prioritised remediation handoff

No remediation was performed. Findings are catalogued with severity/confidence/evidence in `findings.csv`.
Priority = recommended work order; severity = observed impact. Code risks without production usage are labelled separately.

## Ordered backlog

| Order | Priority / findings | Action | Complexity / effort | Dependencies | Acceptance evidence |
|---:|---|---|---|---|---|
| 1 | P0 / F01 | Render Sales trend from resolved facts; retain source comparison table | Small, 0.5–1.5 days | Existing resolved API | Chart and dashboard agree on all 8 dual-source dates; no source-sum revenue |
| 2 | P0 / F06,F09,F10,F23 | Dataset-stage accounting/freshness and expose invoice flags | Medium, 1–2 weeks plus 1–2 days flags UI/API | Stage/grain definition | Parser failure after raw write remains visible; PARTIAL web pull not masked by CSV; flag query count agrees with ledger |
| 3 | P0 / F02 | Diagnose CTB non-JSON recipe response; isolate dataset attempts | Medium, 2–5 days + external unknown | Separate source-request authorisation, stage accounting | Each dataset attempted/accounted independently; contract fixture from actual sanctioned response, no false empty success |
| 4 | P0 / F03,F04,F05 | Supplier/invoice stable IDs, ordinal-tail handling, invoice controls | Large, 1–2 weeks | Raw AJAX↔CSV mapping and identity policy | Two collision keys represented separately; explain/quarantine all 97 current header differences; tail/reorder correction tested in shadow |
| 5 | P0 / F13,F21 | Distinguish purchasing/COGS, tax basis, incomplete ratios/UOM estimates | Medium, 3–6 days | Dataset window completeness, definitions | Purchases never presented as ingredient consumption; ratio coverage carried; unknown WET not fabricated zero |
| 6 | P0 / F15,F18,F19 | API session errors, Z-report gate contract, operand auth | Small/medium, 2–4 days | Role/route policy | 401/403 JSON distinct from absence; token webhook gate tested offline; derived/joins cannot bypass constituent permissions |
| 7 | P1 / F07,F08,F20 | Preserve source identities/costs; canonicalise raw recipe links and field lineage | Medium/large, 1–2 weeks | P0 identity + request window recovery | 639 links / 502 sales codes reconciled; cost/tax/discount controls; PDF evidence IDs recorded for new parses |
| 8 | P1 / F17,F22,F11 | Obtain actual atomic POS, Deputy/OpenTable, ingredient/stock input contracts | External-dependent, estimates in report 10 | Owner-authorised source setup/access | Real sanitised payload contracts and manifest population; no synthetic transactions or guessed ingredient joins |
| 9 | P1 / F12 | Define valid-time and system-time contracts, correct as-of views | Medium, 3–6 days + migration review | Business effective-time policy, source correction semantics | Independent as-of tests at known corrections; no overlapping current/system histories; valid-from not misrepresented ingestion time |
| 10 | P2 / F16 | Typed metadata + bounded relational query planner | Large, 2–4 weeks backend | Stable keys/grain/permissions/control sums | SQL-equivalent reviewed plans for supplier purchasing/product-days; fanout/resource/access rejection tests |
| 11 | P2 / F16,F19,F20 | Versioned calculated metrics and reusable saved datasets | Large, 2–4 weeks | Planner and semantic contracts | New owner-defined cross-domain calculation without Java; definition/version/operand permissions/lineage retained |
| 12 | P3 / F14,F16,F20,F23 | Explorer joins/filter/group/save, comprehensive drilldown and AI plan tools | Large, 1–3 weeks initial UI/AI | Query layer + lineage/metadata | Owner/AI discovers only authorised schema and reaches all available contributors; incomplete/denied data explicitly labelled |

Efforts overlap; this is not a sum-of-estimates project quote. Start with three outcomes:
**consistent existing numbers → recoverable identified facts → flexible authorised questions**.

## Investigation items before implementation decisions

- Recipe endpoint external cause: current evidence establishes non-JSON at getAllRecipes, not its cause.
- Expected source populations/trading calendar: never use raw-page counts as record coverage.
- Five raw invoice keys missing canonical: compare status/export scope and excluded header-only records;
  do not assume each represents a lost payable.
- 97 header/line differences: analyse colliding identities, repeated source lines, discounts/freight/tax and
  source corrections. Monetary aggregate projection passes do not explain them.
- One current tail candidate: check whether the last received file is a complete invoice replacement or
  a partial adjustment. Model removal semantics explicitly before retiring rows.
- Six minimal recent CSV deliveries: discover operator/source purpose and post-storage outcome from authorised
  historical logs; absent FK does not distinguish idempotence from failure.
- PDF enrichment accuracy: approved stratified sample by extractor/source layout; store extraction outputs and
  independent controls before broad replays. Do not invoke live LLM/PDF ingestion during audit follow-up.
- Lightspeed dates/grain: obtain actual scheduled report/filter policy; atomic and aggregate historical bodies differ.
- Authenticated screen/API contract: existing owner session read-only checks in report 12.

## Foundational implementation tests to request (not run here)

Use isolated fixture/Testcontainers databases, never production:
post-raw parser/projection failure, legitimate empty response, source schema drift, cap/pagination truncation,
duplicate/partial deliveries, two suppliers sharing invoice number, changed invoice row order/length/date,
raw contributor and PDF field evidence, unknown versus zero, missing operand/dates, relationship fanout,
and negative permission tests on every base field used by derived/saved/AI queries.

Existing focused tests inspected include GrainAggregatorTest, CtInvoiceCsvParser/IngestServiceTest,
InvoicePdfEnrichmentServiceTest, CanonicalInvoiceLineEnrichmentIntegrationTest and Lightspeed controller/parser tests.
They validate ordinary parsing/enrichment/missing-day behaviour, not production source completeness or all defects above.

## Additive migrations and rollback considerations

Use new source identity/link/stage/parse-output/fact tables and shadow projections first. Preserve old UUID history
and raw evidence. Any backfill must record parser version/window confidence and reconcile controls before switching
consumers. Keep old APIs behind compatible adapters while new query definitions become authoritative.
Rollback is consumer selection back to validated prior definitions, not destructive deletion of new evidence.
Database/role/source changes and deployments require separate approval.
