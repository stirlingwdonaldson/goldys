# Product classification repair — 3 November 2026

Decision: extend the existing browsing taxonomy; replace its financial interpretation with
independent, explicit reporting policy. A ledger/identity rewrite is not justified by this task.
Source-native identity already exists in qualified product sales. Promoting a normalized name
into a universal product identity would discard that work and conflate unrelated observations.

The current tree cannot simultaneously encode product type, stock format, storage, ingredients,
business area and lifecycle. Keep it for browsing and model category suggestions. Append reviewed
reporting revisions keyed by an explicit scope/source/identity and effective date. Dimensions:
sales nature (food/beverage/other/unknown), one approved spreadsheet stock bucket, item role,
business area, lifecycle. Unknown is meaningful. Meat/frozen overlap requires a reviewer decision;
no universal precedence is invented. Merchandise is OTHER even beside beverage variance.

A PRODUCT_NAME mapping is an explicitly reviewed compatibility alias for the existing resolved
name-grain sales consumer, not a new source identity. Reject known identity/recipe conflicts.
Hold those until source-specific identity/equivalence work is reviewed; do not collapse 216 CTB
alias keys with multiple qualified IDs. Inventory mappings use CTB recipe/stock IDs or exact
waste-line refs. Wastage has no reliable stock ID: only exact waste-line and positive recipe-ID
mappings may join its projection. Never join an inventory ingredient to a similarly named dish.

ASSIGN/FULL become preview-only, preserving existing assignments including all manual corrections.
Every product result, including high confidence, requires explicit curation. Jev can abstain;
ambiguous identities/recipe associations are held before calling it. Empty taxonomy preserves
assignments. Source text is untrusted data in both model prompts. Validate choices/probabilities
and naming output. Existing manual move records approval; there is no bulk preview apply path.

Version approved business context in a UTF-8 application resource, maintained through reviewed
code changes. This includes the same-business café history, unknown identifiers/dates, and the
owner's explicitly tentative 2022 closure hypothesis. Persist the exact context, category
criteria, bounded candidate evidence, manual exemplars and model configuration with each run;
persist probabilities, model response version and proposed changes, plus naming inputs/outputs.
No prompt can reconstruct missing source IDs. Embeddings use scoped item evidence without a
shared café-history prefix that would move every vector similarly. The taxonomy namer receives
business/reporting requirements, evenly sampled clusters and the same versioned context.

Reporting policy is curated operational state, not a fabricated source fact or disposable resolved
row. Append revisions with authenticated actor, reason and effective date. No mappings, hierarchy
repairs or assignment changes are seeded by migration. Existing canonical and raw history remains
intact. Current corrected mappings can intentionally restate dates on/after their effective date;
prior revisions remain available for audit. Existing browsing assignments are still current-state,
not a new bitemporal taxonomy history. Do not claim historical as-of classification reproduction.

See the [investigation and operator review](../audits/product-classification-2026-11-03/README.md)
for evidence, evaluation limits, concrete proposed changes and activation boundaries.
