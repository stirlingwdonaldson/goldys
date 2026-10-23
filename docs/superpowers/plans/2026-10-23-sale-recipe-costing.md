# Sale recipe costing implementation plan

Date: 2026-10-23. Status: draft; planning only. Branch: `codex/sale-margin-plan`, baseline `051d0012`.
Design: [sale-recipe-costing-design](../specs/2026-10-23-sale-recipe-costing-design.md).

## Confirmed starting point

Raw catalogue contains recipe **722 `$28 Steak Frites`**, from 15 October Melbourne time (14 October UTC) onwards. It has no cost/ingredient delivery and no canonical cost or resolved recipe row. The current source sale link remains unassigned. Fix **recurring recipe details**, **sale association**, and **margin arithmetic** as distinct defects; never substitute recipe 710 or 706.

Evidence: latest catalogue raw ID `81276941-7cf5-46f6-beea-6bb6bd7a8e90`; cost snapshot `6e460748-1f1f-483a-9f5e-f860025cf4ce`; unassigned link `a8bf328b-a31e-45c4-b060-79fda985ff24`; example item report `e1b13f9e-39c3-4971-80f3-fa35d977d2d4`; payment report `9c96e847-70a7-4b30-9b25-7e4753061943`.

## 1. Contain the incorrect figure

Files: `frontend/components/sales-detail/sale-summary.ts`, `sale-summary-view.tsx`, their tests and any affected fixtures.

- Remove authoritative-looking margin/profit derived solely from POS cost. Do not turn all zero costs into null globally: preserve source evidence and allow future verified recipe zeros.
- Show a clear recipe-costing-unavailable state until the new endpoint provides qualified results. Leave receipt totals, tips and payment reconciliation unchanged.
- Regression: the real two-item $43 example must not show 100% or $43 profit. Test null/zero/nonzero POS costs cannot bypass the recipe requirement.
- Fast gate: targeted sale-summary and view Vitest tests. Full frontend gate before finishing the implementation slice.

This slice is independently shippable and does not wait on source access or database changes.

## 2. Prove the missing recipe detail path

Read-only source discovery, before connector changes that depend on new evidence:

- Through existing CTB authenticated access, fetch `GetSelectedRecipeFullRecords` for `[722,57]` with `recipeFolderIds=[]`, plus `GetIngredientsIncludingSubRecipeIngredients` for 722. Capture successful responses byte-faithfully before parsing on the eventual connector path.
- Verify recipe IDs, portions, per-portion cost and tax basis. Compare to the owner's recipe page, including BOH / Seasonal menu if the source response exposes it. A name-only catalogue cannot verify folders.
- Compare current catalogue IDs with returned cost IDs; produce a count/list of missing recipes and failures. Probe only enough batches to establish practical limits; do not refetch all ingredient details as discovery.
- If access fails, record the blocker and stop dependent ingestion/cost changes. Continue independent UI/payment work. Never fabricate recipe 722's cost or call a synthetic fixture discovery.

Success means a real 722 cost response and a coverage result, not merely an HTTP 200. Folder hierarchy is not required for the costing correction, but missing detail/cost is a separate tracked defect.

## 3. Refresh costs during the existing scheduled CTB pull

Files: `connectors/ctb/CtbConnector.java`, `CtbConfig.java`, existing `CtbClient.java` methods, `CtbRecipeCostParser.java` if validation needs strengthening; `canonical/RecipeCostIngest` and existing batch service; matching tests. Reuse `CtbDetailBackfillConnector`'s request shape, not its unrelated full-backfill orchestration.

- Replace raw-only recipe catalogue handling with raw-first collection of IDs followed by bounded recipe-cost requests; persist each response under `ctb-recipe-cost` and batch canonicalize after validation.
- Preserve the existing per-dataset failure isolation: failed costs must mark partial/failure while other datasets continue. Verify omitted/duplicate IDs and invalid numeric types; never coerce missing cost to zero. Only received facts may supersede canonical versions.
- Reuse current recipe projection events and avoid rebuilding once per recipe. Preserve idempotence and provenance on unchanged and changed re-deliveries.
- Verify the existing recipe screen can include 722 once its cost is collected. Make catalogued-but-uncosted coverage visible through existing ingestion failure/coverage reporting, without adding a full recipe-master domain merely for this task.
- Include a narrowly scoped 722 ingredient recovery using existing ingredient ingest/replay machinery. Schedule broader ongoing ingredient refresh only after request-load evidence; track it explicitly if deferred.
- Tests: `CtbConnectorTest`, `CtbRecipeCostParserTest`, `CanonicalRecipeCostServiceTest`, `RecipeProjectorTest`, source-to-canonical integration with 722, missing IDs, partial failure, repeat delivery and a cost change. Real responses become minimal sanitized fixtures only after capture.

Do not enable production startup backfill flags or run a production refresh in this planning step.

## 4. Preserve sale adjustment evidence and correct price semantics

Files: `connectors/lightspeed/LightspeedSaleItemCsvParser.java`, `LightspeedSaleItem.java`, `LightspeedSaleItemIngestService.java`; `canonical/SaleItemInput.java`, `CanonicalSaleItem.java`, `CanonicalSaleItemService.java`, query views; a new Flyway migration; parser, ingest and PostgreSQL integration tests.

- First measure repeated receipt IDs: distinguish identical tender-join copies from rows with different adjustment components. Document the chosen grouping/identity in `docs/connectors/matching-and-identity.md` before implementation. Do not sum duplicated line revenue or overwrite distinct components.
- Preserve source final revenue ex/inc GST, pre-adjustment gross, adjustment totals and promotion/type/reason/level. Use nullable typed fields; introduce a child representation only if observed multi-component rows require it. Extend bitemporal equality so changed evidence supersedes properly.
- Keep legacy `soldPriceIncTax` evidence compatible. Stop interpreting it as unit price; derive a displayed effective unit revenue from final line total / quantity only where the validated units support that meaning, clearly distinct from listed price. Otherwise show quantity and total.
- Old reports without columns remain parseable with unknown adjustment evidence. Final line amount already includes adjustments; never apply them twice.
- Regression fixtures: $28 special with no adjustment; $14 + $0.23 surcharge; $10 - $10 comp; two $18 spritzes; split Cash/Card duplicate receipt lines; missing columns and genuine multi-component evidence if found.

Follow `.claude/skills/add-migration/SKILL.md`; choose the next migration number at implementation time (baseline highest is V100), never reserve a stale number or edit an applied migration.

## 5. Resolve recipe-backed line costing and expose it

Likely new files: `reconciliation/ResolvedSaleLineCosting*`, `SaleLineCostingProjector`, projection listener and query implementation; `semantic/SaleCostingQuery` and typed results. Extend `StartupProjectionSeeder`, existing canonical query facades, `application/SaleItemReportingService`, `api/SaleItemController` and architecture tests. Add migration for the disposable projection and indexes after evidence fixes its shape.

- Project current source lines with sufficient source identity/version and provenance to detect stale or mixed results. React to sale-item, recipe-cost and link changes. Start with bounded date/receipt rebuilds where source events provide scope; measure any all-line rebuild before adopting it.
- Prefer explicit valid links; permit a unique exact normalized recipe-name match only for unassigned cases. Retain numeric tokens and detect collisions. Record association method; do not alter canonical CTB links or source systems. Treat conflicting/inactive/ambiguous candidates explicitly.
- Before coding the fallback, generate a real-data hit-rate/collision report for explicit links, proposed exact-name fallback and unmatched lines. Require 722/57 in the expected matches. A weaker fallback is not allowed just to raise coverage.
- Compute quantity times recipe per-portion cost with BigDecimal. Resolve zero, missing, negative/refund quantities, modifiers and portion units explicitly from evidence. Keep source POS cost separate.
- Endpoint: `GET /api/sale-items/costing?from=...&to=...&saleNumber=...`. Require exact saleNumber, validated date bounds and both `sale-items.metrics` and `recipes.cost` READ. Reuse existing error and permission patterns.
- Return per-line recipe, association method, cost observation date/source, cost/revenue values and qualification; return whole-sale totals and margin only with full comparable coverage. Totals must not use the existing 200-row browser page as the sale boundary. An incomplete/stale projection is explicit.
- Verify ex-GST cost basis and surcharge meaning before producing a numerical margin. No confirmed cost-effective date means an explicitly labelled current-recipe estimate; no guessed historical actual cost. Zero revenue produces no ratio.
- Tests: projector/rebuild integration, exact 722 and explicit 57 matches, collisions, missing/verified-zero costs, quantity and refunds, comp, cost/link supersession, stale projection, complete large sale, permission denial and `ArchitectureBoundariesTest`. No business consumer reads raw/canonical to calculate the metric.

## 6. Wire costing, adjustments and card labels into the receipt

Files: `frontend/lib/api/types.ts`, `live.ts`, `demo.ts`; `sale-summary.ts`, `sale-summary-view.tsx`, `sale-items-tab.tsx`, `payments-tab.tsx` as needed; related tests.

- Follow `.claude/skills/frontend-api-method/SKILL.md` with matching live/demo types and behavior. Add bounded costing query alongside existing receipt queries. Keep ordinary receipt details available if costing fails or is denied; expose the specific error/locked state.
- Display backend figures, per-line recipe/cost provenance and missing coverage; no client POS fallback. Label current-recipe estimates and observation date. Missing, loading, denied and failed states must not show zero or an old sale's cached metric.
- Replace guessed discount messages with actual source adjustment explanations. Fix quantity/unit-price display.
- Use existing `lspayPaymentMode` to display Card for Card Payment, retain provider name, and show Cash from explicit type. Mr Yum remains a channel with unknown underlying method unless observed. Tendered must not label a payment cash or imply denominations/change.
- Tests: live/demo contract, the reported sale, missing/denied costing with receipt still visible, adjustments, multi-quantity display, split tenders, Cash/Card/unknown channel and sale switching/loading.

## 7. Reconcile preview, verify and prepare rollout

- Update `docs/connectors/source-access.md`, `matching-and-identity.md`, relevant metric-definition notes and the operation/replay instructions. Record remaining ingredient-refresh and folder-path limitations explicitly.
- Prepare a read-only coverage/replay preview from retained raw samples: requested vs returned recipe IDs, canonical/projection coverage, match hit rate, ambiguous/unmatched names, distinct receipt counts and before/after line revenue. Count distinct sales/lines; do not add overlapping report deliveries together.
- Acceptance: the example contains recipe 722 and recipe 57 when source costs are collected; no fabricated exact margin before basis verification. Revenue/payment totals stay $43. Any cost is traceable to a raw delivery. Replay is idempotent and leaves raw bytes untouched.
- Fast checks while editing: targeted tests or `./gradlew fastTest`; focused Vitest. Full backend gate: `./gradlew test spotlessCheck` against real PostgreSQL 16 via Testcontainers. Full frontend gate sequentially: `bun run typecheck && bun run lint && bun run test && bun run build`.
- Run Ripwire change-check and quality-delta for implementation diffs before declaring them ready. Its current graph finds one direct caller of `summariseSale` and tests, but Java callback/interface reach is incomplete; a zero reported `CtbConnector.fetch` caller count is not proof of no runtime impact.
- Only after explicit approval: push/create PR, watch CI to completion, deploy, then perform separately reviewed bounded runtime refresh/replay and read-only postchecks. No push, production writes, deployment or source writes are authorized by this plan.

## Work split and risk

Suggested delivery: (1) containment and payment display, (2) recurring cost ingestion plus measured 722 recovery, (3) adjustment preservation and backend costing, (4) receipt integration and authorized recovery. Keep commits atomic within those slices. The largest risk is presenting current/source-incomparable recipe cost as historical actual profit; fail explicitly until evidence supports the claimed figure.

No application implementation/tests were run while writing this plan. Planning verification consists of source/test inspection, Ripwire structure queries and read-only production raw/canonical/resolved checks. Live cost/ingredient discovery and folder verification remain outstanding.

## Implementation outcome (23 October)

The implementation reuses concurrent recipe PR #211, fast-forwarded locally at `3bb30f80`,
for recurring complete recipe collection and catalogue visibility. It does not duplicate those
connectors or enable their production flags. Real source coverage there includes recipe 722.

V103 uses disposable SQL views instead of storing another copy of every receipt line; snapshot
reads combine these with existing resolved recipe selection. A dedicated cost service/controller
keeps its extra recipe permission independent of source browsing. Numerical margin remains null
because comparable source tax bases remain unverified. Current recipe observations and actual
line prices are displayed with qualifications. Source adjustment retention, duplicate protection,
card mode display and the reported sale regressions are implemented. Historical enrichment replay
is deliberately not automated: the existing replay has no newer-source guard for this repair.
See `docs/operations/sale-costing-rollout.md` for the bounded rollout and remaining source gates.

The owner's final tax-basis decision authorizes assuming CTB costs exclude GST.
`CTB_RECIPE_COSTS_EXCLUDE_GST` defaults true and false withdraws the assumption. The receipt
labels numerical results estimated and shows the assumption; missing/stale costs, missing tax
revenue and zero denominators remain explicit. This supersedes the earlier “numerical margin
remains null” implementation note. See the rollout document's assumption decision.
