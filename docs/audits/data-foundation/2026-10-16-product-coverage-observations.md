# Product identity, calendar coverage and dated inventory observations

Implemented locally on `codex/product-coverage-observations`, based on `1b3500e166027f8353c631e25ffaf12f6ea7ab5a`.
No production migrations, replay, ingestion, service changes, deployment or push are included.

## Source evidence

The approved read-only retained-ledger probe inspected three CTB sale-item pages (379 rows), three
stock-count detail pages (711 rows) and three waste detail pages (3 rows). All nine payload digests
matched their authoritative bytes. Payloads stayed in memory; no raw or personal fields are copied
into this report. Sale dates are null on all sampled product rows; their canonical provenance
contains the request day. The historical product receipts lack request references; new live pulls
already persist those references. No date is invented for ambiguous retained evidence.

The updated product parser read 3/3 pages and 379/379 rows, with source product identity, quantity
and amount present on all 379 rows. This is a bounded hit-rate/schema check, not evidence of global
identity uniqueness, export completeness, correction policy, tax basis or a historical repair.
No same-name/multiple-code case appeared in these samples; edge regressions use isolated fixtures.

## Changes and requirements

| Area | Result | Remaining qualification |
|---|---|---|
| G08; BR-001/009/010, INT-004; proposed DR-018/019 | CTB daily products retain code/site identity, group observations and all page contributors; overlapping source groups count once; batches publish after pagination | Business projection still reconciles unambiguous names; reviewed cross-source hub/variant and category history remain open |
| G13/G15; BR-006/007/008; proposed DR-040/041, MET-008, REL-001/004/005 | Sales, food/beverage, product, labour, booking, inventory and payment daily-grid metrics carry observed, stored-null and absent dates, plus prominent incomplete-subtotal notices and provenance | A calendar grid cannot establish expected trading population or within-day/source completeness; no missing-money inference or five-percentage-point bound |
| G06; BR-003/006/009 | Paged dated count/waste observations preserve units, zero, nullable costs, source references and partial/locked stocktake context | No current/usable-stock balance, conversion, consumed cost, tax-normalized expense or valuation metric is asserted |
| Access boundary | Observation reads require both stock and detailed-cost permissions; no new grants; raw lineage stays internal | Owner/designated-administrator raw access continues separately; no new forecast driver exposure or AI tool |

New source-qualified canonical daily facts keep stable identity across a rename. Every old/new alias
on the day is reprojected. Same-name products from the same source produce unavailable conflict
values, even with a source override. Equal-value receipts are retained, and observed/older receipts
cannot manufacture new versions after a correction. Legacy canonical history is not re-keyed;
compatibility reads suppress legacy name aggregates for a source/day once qualified rows exist.
Absence does not create deletion or zero facts. The older group-financial source ref remains
code/date/group; a collision between different known sites rejects visibly rather than overwriting.

`resolved_inventory_observation` is a current-version SQL view, avoiding a new event/rebuild system.
Business API records omit raw UUIDs and canonical version IDs. The Kitchen list has independent
historical date/kind filters and server pagination. Source cost fields are labelled unverified and
formatted without an assumed currency; quantities retain four decimal places where present.
Missing and zero are distinct, and empty pages explicitly do not establish zero stock or waste.

## Migration and rollout implications

V96 adds the product source reference, contributor table and quantity/amount/name/site observation
fields, and changes the current-product uniqueness index to source/reference. V97 creates the
disposable observation view. Both apply only in disposable test databases in this work. No remote
branch had V96/V97 migration filenames at the checked refs. They must be checked again when landing.
Legacy rows are preserved; no historical backfill is run or implied. Production migration and any
later replay/repair require separate approval and a concrete reviewed runbook.

## Verification

Final local gate results are recorded below after completion. Regression coverage includes source
code/site collisions, rename invalidation, contributing-page dates, overlapping groups, corrected
and equal-value intermediate replay, conflict before override, calendar missing reasons/explicit
zero/historical inputs, observation correction/date movement/pagination, permissions, unit and
unknown handling, and frontend qualifications. Existing fixed tools/widgets retain the shared
notice contract; this work adds no new tool or export surface.

The table-oriented audit schema extractor does not enumerate SQL-view columns; V97 and its real
PostgreSQL integration tests are the schema evidence for the observation view. Historical production
coverage/readiness evidence is not regenerated or claimed current.

Frontend final gate passed: `bun run typecheck`, `bun run lint`, 617 tests across 104 files, and
`bun run build`. The existing Vite configuration warning remains. No browser end-to-end session
or model-service test was run. The audit harness's eight standard-library Python tests passed and
code-derived table schema/lineage artifacts were regenerated; their name-based dropped-field
heuristic still reports `siteNumber` despite its encoding in `source_product_ref` and retention in
source observations. This heuristic is not evidence that the updated path drops the site.

Ripwire review is not clean. The final pre-commit working-tree delta reports 118 gating findings;
these remain visible, without blanket acknowledgements. They chiefly concern duplication/helper
clones, churn and verbosity. The large new observation UI function was split into row/table/state
helpers, removing its introduced complexity warning. Product `recordAt` complexity rises from
3 to 13 (below the tool's 15 threshold, reported minor); this includes receipt replay and version
handling. Compatibility DTO constructors intentionally keep existing call sites. Ripwire's
arity check flags three old seven-argument `ProductSalesInput` calls despite the retained
seven-argument constructor; compiler and integration tests verify that overload. Framework
injection/method-reference paths and JUnit tests also appear as static dead-code candidates.
The capped PR-context report is a navigation aid, not a full automated correctness verdict.

The new batch `add` edit-check also attributes unrelated collection `add` calls to it (21 static
arity warnings); those call sites are not calls to the new batch method. Actual connector/batch
calls are compiled and tested. Sale-item/deleted-sale grid executors and derived/ranked results
retain their existing provenance shape; this increment does not claim calendar coverage metadata
for every metric family or full contributor lineage for every report.

Backend final gate passed: `spotlessApply test spotlessCheck` in 3m 27s, with 1600 tests
across 348 suites, 0 failures, 0 errors and 2 skipped. The skipped cases are
not claimed verified. This includes real PostgreSQL/Testcontainers migrations V96/V97, architecture
boundaries, API coverage serialization and the real sales-executor → reporting tool/widget
qualification regression. Earlier runs found stale notice assertions; the final run includes their
corrected observed/unresolved/absent expectations.

The original checkout remains on main `54af2543391615fae20306cc5c85090929c8c99e` with its existing
untracked requirements/plans preserved. No dependencies were installed. No push, PR, CI run,
production migration/repair/replay, external source write, restart or deployment was performed.

Committed-range Ripwire check (`1b3500e166..5bb012566e`) reports 100 gating findings, with churn
unavailable for the ref-pair comparison. This is not a clean static-analysis result; the remaining
trade-offs described above are recorded rather than suppressed. The application commit is
`5bb012566e4581cd518b92eee68c3caf43162b6d`; the subsequent report-only commit changes no application code.
