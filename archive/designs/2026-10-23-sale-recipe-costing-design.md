# Sale recipe costing, adjustments and payment indicators

Date: 2026-10-23. Status: draft for implementation planning; no production change authorized by this document.
Code baseline: `051d0012dd8666bda09cd26ab11488ef329364c2`.

## Problem and verified evidence

The sale receipt computes gross margin in the browser from Lightspeed `costIncTax`, without a recipe lookup. Non-null zero is accepted as complete cost. Sale **SP-100 1021082623**, trading date 2026-10-21, contains one $28 Steak Frites and one $15 Swiftsure Sauvignon Blanc glass; both raw POS costs are zero. The receipt therefore claims $43 profit and 100% margin. Across that trading date, 786 of 1,161 current item rows have zero POS cost.

The owner's correction is verified: **recipe 722, `$28 Steak Frites`, exists in the retained CTB recipe catalogue**. It first appears in retained deliveries on 15 October Melbourne time (14 October UTC) and is present in the latest delivery, `81276941-7cf5-46f6-beea-6bb6bd7a8e90` (23 October). The catalogue response has recipe ID/name only; the owner's BOH / Seasonal menu folder path has not been independently verified.

There are **zero** canonical cost versions, canonical ingredient versions, raw ingredient deliveries (`recipe:722`) or resolved recipe rows for 722. The only `ctb-recipe-cost` delivery is from 12 October (`6e460748-1f1f-483a-9f5e-f860025cf4ce`), before 722 appears in retained catalogues. This is an ongoing-detail-ingestion gap, separate from the sale-margin bug. `CtbConnector.fetch` refreshes recipe names but neither costs nor ingredients; the detail connector is a one-off backfill. `RecipeProjector` iterates cost records, so catalogue-only recipes never enter `resolved_recipe`.

A second independent association gap remains: current CTB sale-item stock code 23703576, `$28 Steak Frites`, has recipeId -1 and no recipe name (`a8bf328b-a31e-45c4-b060-79fda985ff24`). The presence of recipe 722 does not fix this source link automatically. Wine stock code 22857146 explicitly links to recipe 57, currently $2.88 per portion. Do not substitute regular Steak Frites (710) or Steak Frittes (706).

The sale-item raw delivery `e1b13f9e-39c3-4971-80f3-fa35d977d2d4` already includes adjustment type/reason/level, promotion name, gross sales and adjustment amounts. These are ignored by `LightspeedSaleItemCsvParser`. The example has no adjustment: $28 is a separately priced special. Other observed rows have gross $14 + surcharge $0.23 = final $14.23, or gross $10 - discount $10 = final $0. Adjustments are already in final line revenue.

`Advanced Dimensions Sold Price Inc Tax` is not consistently a unit price: two $18 spritzes have quantity 2, sold-price field $36, final amount $36. The UI's unit-price display and inferred-discount checks are consequently unreliable. Ten bounded recent reports (32,143 delivered rows, overlapping windows) had no named promotion or explicit happy-hour name/reason. Happy-hour semantics remain unverified.

The raw payment `9c96e847-70a7-4b30-9b25-7e4753061943` identifies this sale as Cash, $43. Card mode exists on other payments and is retained through the API. Tendered is populated for card payments too; it is not evidence of physical notes. Source payment channels such as Mr Yum must not be guessed into cash/card categories.

## Intended behavior

- Receipt costs come from matched CTB recipes, per portion times sold quantity. POS cost remains source evidence, never a silent recipe-cost fallback.
- A complete sale margin requires every relevant line to have an unambiguous recipe association, usable portion cost and comparable tax basis. Missing data produces an explicit reason and no whole-sale percentage. A verified zero recipe cost is allowed; POS zero alone is not verification.
- Show recipe identity, association method and cost observation date. Historical sales costed with a current recipe observation are explicitly **estimates**, not historical actual costs. Do not invent historical effective dates or an arbitrary staleness threshold.
- Use actual final sale revenue once. Surface discounts/comps/surcharges from observed fields; never infer a promotion solely from catalogue-price differences. Do not subtract already-applied discounts again.
- Display Card when observed mode is Card Payment, keep the provider label, and preserve Cash as Cash. Leave unclassified channels explicit. Do not infer a method from Tendered.

## Smallest architecture that preserves the read boundary

Keep existing source-detail endpoints and raw evidence intact. Add a resolved per-receipt-line costing projection, implemented in `reconciliation`, keyed by the existing logical receipt-line identity. It contains the observed quantity and final revenue, selected recipe and cost provenance, association/cost qualification and replayable adjustment evidence. Its inputs remain canonical facts; no facts exist only in the projection. Extend query facades narrowly to expose needed source references/dates rather than leaking repositories across packages.

Add a bounded `SaleCostingQuery` semantic interface implemented over that projection and a `GET /api/sale-items/costing` endpoint with required exact saleNumber and inclusive from/to dates. Return line costing plus backend totals/qualification. Reuse `SaleItemController` / `SaleItemReportingService`; require both `sale-items.metrics` and `recipes.cost` through `PermissionService`. A denied recipe-cost read must be explicit and must not hide the ordinary receipt/payment details behind the new request. No new grant matrix is inferred.

The frontend adds one typed live/demo API method and loads costing alongside the existing receipt data. The backend owns decimal money calculations; the browser formats the result. A failed/denied/incomplete costing request never falls back to browser POS-cost arithmetic. Whole-sale costing totals must cover the entire bounded sale, not the receipt's first 200 rows. Keep large-sale completeness explicit and avoid publishing totals from mixed source/projection versions.

### Association order

1. Prefer a stable product/stock identity where actually populated and verified; the example's POS number/SKU are blank.
2. Otherwise match the sale name uniquely to CTB link descriptions, then use a valid linked recipe ID. Multiple conflicting candidates are ambiguous, never first-match wins.
3. Where the source link is unassigned (negative sentinel), allow a unique exact normalized recipe-name association, recorded as a derived name match. Use existing `ProductNameKey`, preserving numeric price tokens: `$28 Steak Frites` must remain distinct from `Steak Frites`. Recipe 722 is the real acceptance example. An explicit conflicting source association must be surfaced rather than overwritten by fallback.
4. Blank, duplicate or unresolved candidates remain uncosted. No fuzzy matching or price-prefix stripping. Do not mutate CTB or manufacture a source link in canonical data.

Before implementation, measure collisions and match coverage on real names, including the distinction between intentionally inactive links and unassigned sentinel rows. The exact-name rule is a proposed resolution policy, not an assertion that all equal names represent the same recipe.

### Revenue and cost basis

Target business formula is `(net revenue excluding GST - recipe ingredient cost on the same tax basis) / net revenue excluding GST`. Use source-reported tax-exclusive final revenue where available. Keep tips outside item revenue. Preserve allocated surcharge separately; verify whether it is a payment surcharge or another order surcharge before deciding its business inclusion. Test both ordinary and zero-revenue comps; division by zero yields no percentage.

**Gate numerical margin:** verify CTB cost tax basis and sold-quantity/portion units from real recipe 722 and at least one beverage example. The current recipe query already withholds margins because this basis is unverified. Collecting a nonzero cost alone does not clear this gate. Until cleared, show recipe association/cost observations with margin unavailable. If historical cost validity cannot be established, label the qualified calculation estimated gross margin and expose its observation date.

### Recurring recipe ingestion

Use `CtbClient.getAllRecipes` and `getSelectedRecipeFullRecords`, the existing raw sink and `CtbRecipeCostParser` / `RecipeCostIngest.recordAll`. Extend the existing scheduled CTB pull, with bounded batches selected from measured endpoint limits, per-dataset failure reporting and one projection event per recorded batch. Validate expected/returned IDs: an omitted recipe is missing coverage, never silently successful or zero cost. Do not rerun unrelated stocktake/wastage backfill to obtain a new recipe cost.

First obtain a real cost response for 722 through the existing authenticated connector access path. The retained catalogue proves identity, not its current cost. Current source authentication and folder schema have not been accessed in this planning session; do not invent fixtures for them.

Ingredient completeness is tracked separately: fetch and canonicalize 722 through the established ingredient endpoint to verify it, then scope ongoing refresh from measured request load and endpoint behavior. Do not block using verified CTB per-portion costs on implementing ingredient roll-up; parent/subrecipe usage quantities remain unproven. Do not quietly expand this fix into a new recipe-folder hierarchy or a full inventory synchronization project.

### Adjustment preservation and cardinality

Preserve the typed business fields necessary to explain final revenue: gross before adjustments, final revenue ex/inc tax, aggregate adjustment ex/inc tax, and observed promotion/type/reason/level with components when the source supplies distinct rows. Keep raw record lineage. Unknown values stay null, distinct from confirmed zero/no adjustment.

Choose the canonical representation only after reviewing receipt-ID cardinality. Ten sampled reports had 17 repeated receipt IDs; the latest examples were identical item rows repeated by Cash/Card tender joins. Do not double-count those copies. Multiple genuinely distinct adjustment components must not supersede one another as though they were corrected copies. Missing adjustment columns in older raw reports produce unknown evidence, not invented zero adjustments.

## Delivery order and acceptance

1. Contain the false metric: remove the POS-derived Gross margin calculation; show costing unavailable until recipe-backed results exist. Preserve receipt totals and payment reconciliation.
2. Repair recurring recipe-cost ingestion and collect real recipe 722 details. Report catalogued-but-uncosted IDs/counts. Verify 722 reaches canonical cost and resolved recipes after an explicitly authorized runtime refresh.
3. Preserve and validate adjustment/unit-price evidence, then project recipe-backed line costing and expose the authorized endpoint.
4. Wire receipt costs, qualifications, actual adjustment explanations and card indicators in live/demo UI. Show per-unit sold revenue only when its semantics are established; otherwise show quantity and line total without a fabricated unit price.
5. Review bounded replay preview and reconciliation totals before any authorized production repair. Separate code rollout from production replay approval.

Acceptance examples: exact special resolves to 722; wine resolves to 57; missing/ambiguous cost suppresses complete margin; verified zero is retained; quantity scales recipe cost exactly once; comp keeps ingredient cost with no divide-by-zero; order adjustments are not applied twice; split tenders do not duplicate items; Card and Cash are distinguished; source names/IDs survive replay; cost permissions and projection completeness fail explicitly.

## Limits and decisions still gated by evidence

Recipe 722 cost/ingredients/tax units and folder hierarchy have not been fetched live. Happy-hour representation is unverified. Historical effective recipe costs are unavailable from current retained evidence. Adjustment multirow semantics need a measured preservation rule. These gate their dependent implementation, not the independent metric-containment/payment work.

No new dependencies, AI tools, global metric catalogue entry, fuzzy matcher, general overrides UI, automatic CTB writes, card-brand enrichment, production scheduler toggle or deployment is included. Architecture source: `docs/system-context.md`, `.claude/rules/architecture-invariants.md`, `docs/architecture/current-state.md`. Source reality: `docs/connectors/source-access.md`; extend `matching-and-identity.md` when the resolution rule is implemented.

## Superseding owner decision (23 October 2026)

The owner subsequently instructed us to assume CTB cost per portion excludes GST and make this
easy to change. Individual-sale estimated GP/margin is therefore enabled with the explicit
`CTB_RECIPE_COSTS_EXCLUDE_GST=true` assumption. Setting false withholds GP/margin without hiding
observed recipe costs. Use reported final ex-GST revenue, or final amount minus reported tax for
older rows; do not assume a GST rate when tax is missing. Show the assumption next to estimated
margin, retain freshness/coverage qualifications and keep historical actual-cost claims withheld.
This supersedes the earlier numerical-margin tax-basis gate; the source basis remains unverified.
