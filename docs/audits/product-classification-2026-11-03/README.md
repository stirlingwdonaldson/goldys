# Classification investigation, implementation and review

Investigated 3 November 2026 against read-only production; implementation starts at `f15526b5`
in branch `codex/product-classification`, worktree `/tmp/goldys-classification`.
Queries use `checks/probe.py` from the production-data audit: session/transaction read-only,
8-second statement timeout, no source-system or database mutation. Evidence files contain product
observations, not credentials. CSV files contain consecutive labelled result sets, not one table.

## Findings and measured coverage

- The curated Beer category is under Food, updated 23 October 09:11 UTC. Actor remains unknown.
  This is a hierarchy error. It is separate from Jev's `porterhouse → Beer, NEEDS_REVIEW, .47`
  assignment and the weak source context (`Porterhouse` has Uncategorised POS observations).
  Recipe 461 is named Porterhouse in All time classic. `Porterhouse (250g )` has FOOD MENU source
  evidence; its normalized alias is assigned to Weekly Specials. A regular steak is not proven
  to be a weekly special. Do not blindly move every Beer assignment into beverage.
- All **14 categories have blank descriptions**. No general Mains/Food category exists. The old
  classifier had to choose among a poorly specified set, including Weekly Specials and Beer.
  A parent change could change financial interpretation of every assigned product, with no
  assignment change. Reporting also accepted NEEDS_REVIEW suggestions as approved assignments.
- **2,594 assignments**: 2,077 ASSIGNED, 517 NEEDS_REVIEW, all JEV at inspection. No manual
  overrides existed in this snapshot. Tests still protect future manual corrections.
- **1,498,758 current receipt lines** (23 November 2020–2 November 2026). Product number is present
  on 51,935 (3.47%), SKU on 1,116 (0.075%), site on 9,833 (0.66%). Category and register labels
  exist on all lines, but 1,506 distinct names appear under Uncategorised. Labels/registers do not
  establish café identity. Category observations change over time: e.g. Margarita has COCKTAILS
  and Uncategorised; Kids Fish & Chippies has Multiple Categories and Uncategorised.
- The `canonical_product` catalog is **empty**: its reporting_group/is_modifier fields cannot
  supply missing classification. CTB `source_product` has **628 rows**, no populated numeric
  source product IDs/categories, but retains source record refs. Four names have multiple catalog
  identities: Coke Zero - Mixer, F - Cauliflower Karaage, Pint/Pot - Philter XPA.
- Qualified CTB sales reveal **216 normalized aliases with multiple source_product_ref values**.
  These can represent variants, historical identity changes or true collisions. They are holds,
  not proof of equivalence or proof that all 216 are unrelated products. The old extractor's
  first-observation selection obscured these distinctions.
- **1,073 recipe links / 447 recipe IDs**, no Lightspeed product IDs, **596 inactive links**.
  Five names have multiple recipe states: .5L Jamsheed Flor de Corazon, Apple Juice - Mixer,
  B - Alkina Kin Grenache 2024, Coke Zero - Mixer, Vegan Option. These include -1 inactive versus
  a positive recipe; choosing the first entry loses the conflict. Duplicate links to the same
  positive recipe (Philter 102) alone do not prove a conflict; multiple product IDs still do.
- **674 complete recipe definitions**, all currently flagged active, 96 folders; **1,299 stock
  lines**, all with stock IDs/supplier observations. Recipe active flags and inactive sale links
  describe different observations; neither certifies current menu availability. Current composition
  does not establish ingredients/costs effective at an old sale date. Aliases remain separate
  observations; there is no new automatic equivalence inference.
- Explicit café names include Parma/Cheeseburger/Chippies/Coke (cafe), sold in 2021–22. Latte - Small
  was observed through 9 December 2022. These are evidence of historical activity, not operating
  boundaries or unique café ownership of coffee. Owner clarified that the café was part of the
  same business and **thinks** it stopped in 2022 based on data. The hypothesis and uncertainty
  are persisted in application context; dates/outlet IDs remain unknown. The dated POS reconciliation
  documents renamed pub dishes; absence of recent sales must not declare those retired either.
- **580 wastage lines** (10 September–2 November): all have quantity/cost/unit; **484 positive
  recipe IDs (83.45%)**, **483 match current definitions**, one lacks a definition. **63 direct
  name matches (10.86%)** to old assignments; **517 unmatched (89.14%)**; **266 missing reasons
  (45.86%)**. Recipe matching is association coverage, not classification or valuation accuracy.
  Stock-only wastage has no verified stock ID, so no automatic supplier/name-based stock join.

For the completed week **26 October–1 November**, 1,034 resolved product/day rows have known amounts;
20 have no category assignment. NEEDS_REVIEW covers 215 rows / **$7,762.5041 absolute amounts**;
Beer covers **$58,588.8022 absolute amounts**, including $1,296.7483 in NEEDS_REVIEW. These are exposure
measures, not net sales/GST-certified revenue or a proposed wholesale transfer between food and
beverage. Source coverage, tax basis and headline reconciliation remain independent gates.

## Smallest supported model and implemented behaviour

Keep the menu category tree for browsing, with approved inclusion/exclusion criteria editable via
category PATCH `description`. **Financial sales nature is no longer inferred from that tree.**
`NEEDS_REVIEW` is uncategorised in category totals. Category totals still reflect current curated
browsing assignments, not a historically versioned classification ledger.

Add append-only reporting revisions, one explicit identity/effective-date decision with independent
sales nature, stock bucket, role, area and lifecycle. UNKNOWN is the default; no mapping seeds or
source facts are rewritten. These dimensions can represent a historical café beverage without
claiming current availability, and a frozen meat ingredient without forcing storage into the menu
tree. A reviewer chooses a single spreadsheet bucket when meanings overlap; no automatic precedence.
Merchandise must have OTHER sales nature, regardless of spreadsheet placement.

PRODUCT_NAME is a reviewed compatibility alias for existing resolved name-grain sales consumers.
Known conflicting identities/recipe associations cannot be approved there. Splitting their reporting
requires reviewed source identity/equivalence work, not another prompt or fuzzy name match. CTB_RECIPE,
CTB_STOCK and exact WASTE_LINE policies preserve source identity. Wastage joins only exact line refs
or positive recipe IDs; stock mappings are stored for later stock consumers, not guessed onto waste.
An exact waste-line decision takes precedence over recipe mapping, including an UNKNOWN withdrawal.

ASSIGN and FULL now save **review previews**, never update assignments. Empty taxonomy does not clear
manual corrections. Ambiguities are held before Jev, and Jev has an Unclassified choice. All confidence
levels need review. Existing authorised manual moves record approved corrections; reruns cannot undo
them. No bulk apply endpoint or production rerun was introduced. The Products screen exposes saved
proposals and item evidence; legacy runs explicitly lack snapshots.

Candidate context retains all distinct source labels rather than a nondeterministic first match,
source-scoped IDs, first/last observations, recipe IDs/folders/active states and up to eight ingredient
observations with supplier/raw evidence. Text is capped at 16 observations/8,000 characters with
truncation notices; conflict detection runs before the cap. This does not synthesize missing identities
or source-effective dates. Embeddings use this item evidence, not a common business-history prefix.
The taxonomy namer gets the versioned business/reporting context and evenly sampled (up to 15) items
per cluster. Bounded source text is data, never instructions; model output remains an untrusted proposal.

`backend/src/main/resources/classification/business-context.txt` is the durable reviewed context.
Maintain it through a reviewed code change; bump its version and cite evidence when approving café
dates/identifiers. Category descriptions hold inclusion/exclusion rules/counterexamples. Runs retain
exact context, category choices, candidate evidence, up to 100 sorted manual exemplars, classifier
alias, embedding policy, returned Jev model version/probabilities and proposed/previous category IDs.
Naming inputs/prompt/output are separately saved. Rolling hosted-model changes preclude guaranteed
bitwise model replay; snapshots explain the evidence and permit recorded-output replay.

## Real-data sample and accuracy

The owner explicitly approved a bounded Jev evaluation using the configured credential without
printing/exporting it. A standalone temporary Java client made **21 requests**; it called no production
pipeline or write endpoint, and removed its temporary JAR. Responses identify **jev-1.14.0**.
The sample has **24 real cases**: current pub items, historical explicitly named café items, food,
drinks, bulk ingredients, menu modifiers, merchandise, ambiguous names and porterhouse variants.
Three conflicts were held (Coke Zero - Mixer, Pint - Philter XPA, Vegan Option).

The saved `evaluation-criteria.json` is a **draft evaluation taxonomy**, including general Food,
Ingredients and Modifiers absent from today's curated tree. It is not secretly installed in production.
Expected labels were reviewed from source observations/descriptions by this investigation; the owner
has not certified every role label. In particular Oat milk's modifier label is an inference, and
Braised Beef Cheek's sold-versus-preparation role is unknown. Preserve these qualifications.

**18/21 agreement (85.7%)** against those provisional expectations; 3/24 identity holds (12.5%);
1/21 model abstentions (Oat milk). Thus 4/24 held/abstaining (16.7%). All 24 remain unapproved for
production reporting until explicit curation; the evaluation is deliberately selected, not a random
production accuracy estimate. Disagreements are retained, not relabelled to inflate accuracy:

| Item | Provisional expectation | Observed | Consequence |
|---|---|---|---|
| Oat milk | Modifier | Unclassified | Safe abstention; evidence does not certify menu role |
| 12 x 80g dill pickle Chappy's potato chips | Ingredient stock pack | Food | Stock versus sold-item distinction needs review |
| Braised beef cheek (recipe 440) | Unclassified role | Food (.71 confidence) | Prepared recipe name does not prove sale-product role; confidence alone would have accepted it |

Porterhouse variants were proposed as Food. Café Parma was Food; café Coke/latte were nonalcoholic;
café canned beer was Beer; three branded merchandise cases were Merchandise. These successes do not
validate unseen products or ingredient/stock allocations. The recorded responses are replayed through
the actual Java Jev adapter in `ReviewedProductSampleTest`; no paid model is called in CI.

## Production change preview — nothing applied

1. Regroup Beer from Food to Beverage after approval; actor behind the old change remains unknown.
   This fixes browsing hierarchy only. It does not prove Beer memberships or determine financial splits.
2. Add/review a general Food/Mains category with explicit criteria. Review Porterhouse (recipe 461)
   into it; review Porterhouse 250g independently rather than assuming Weekly Specials is appropriate.
3. Review category criteria before another taxonomy run. Draft sample criteria and all 24 observed
   outcomes are in evidence. Do not automatically apply 18 agreeing outputs or all Beer assignments.
4. Establish source identity decisions for held aliases. Populate reporting revisions only after
   verifying sales role, date scope, tax basis and exclusions. A deliberately earlier effective date
   restates that reporting interval; it must be explicit in the review reason.

Migration V108 creates structures only. Deploying it does not repair Beer or reclassify any history.
No source system, production classification, ingestion job, mapping or source report was changed.

## Review interfaces and permissions

Existing `products.category` table-driven permissions govern reads/writes; no new role grants.

- `GET /api/products/categories/runs/{id}/preview`: exact context, naming and assignment snapshots.
- `PATCH /api/products/categories/categories/{id}` accepts `description` as approved category criteria.
- Existing manual move endpoint records an explicit approved browsing assignment.
- `GET /api/products/categories/reporting-mappings?to=YYYY-MM-DD`: effective-dated policy revisions.
- `POST /api/products/categories/reporting-mappings`: append a review, authenticated actor stored.
  Required fields are `scope`, `sourceSystem`, `entityKey`, `effectiveFrom`, `salesNature`,
  `stockBucket`, `itemRole`, `businessArea`, `lifecycle`, `reason`. Enum names match
  `semantic/ProductReportingMapping.java`. PRODUCT_NAME uses source RESOLVED and a normalized,
  unambiguous observed alias. Other scopes use CTB; numeric IDs must be positive and observed.
  WASTE_LINE uses the exact observed ref, e.g. `wastage:<recordId>:<detailId>`.
- `GET /api/products/categories/reporting-mappings/wastage?from=...&to=...&page=0&size=50`:
  itemised current wastage evidence and mapping revision/scope/reason, including unknowns.
  Requires BOTH inventory.cost READ and products.category READ, before reading. Range <= one year,
  page 0–10000, size 1–200. Source cost/quantity/unit are separate: no guessed cost/unit conversion.

Append a same-key revision with UNKNOWN dimensions to withdraw a policy from its effective date.
Old revisions remain. There is no bulk update/delete or unreviewed mapping inferred from taxonomy.

## What this enables, and remaining work

Enables saved, explainable model proposals; persistent business context; reviewed independent sales
and spreadsheet stock classifications; explicit uncertain states; itemised wastage with recipe/line
mapping and evidence; safe manual curation without automatic historical rewrites.

Food/beverage metrics now require explicit reviewed mappings; with no mappings they become unavailable
instead of reflecting Beer → Food. The existing 95% absolute eligible-product-amount coverage rule is
retained, not a claim of headline completeness. No weekly food/beverage actual field is activated.
WeeklyReportService still withholds those fields. Before enabling them: verify revenue/GST basis,
source coverage and reconciliation, date attribution, exclusions and reporting allocations.

Itemised waste does not certify financial wastage totals: reasons, staff meals/drinks/knock-offs,
unit conversion, unit-cost basis and valuation still need review. CTB_STOCK mappings do not implement
stock variances: opening/closing counts, receipts/movements, recipe consumption, units and valuation
remain necessary. No labour/bookkeeping/unrelated report work is included.

Validation results are recorded in `validation.md`. Push, PR/CI, merge, deployment and production
classification/curation need separate approval under AGENTS.md.
