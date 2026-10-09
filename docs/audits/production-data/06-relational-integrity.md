# 06 — Business entities and join integrity

`current-entity-model.mmd` reconstructs all 34 deployed tables. Solid edges are PostgreSQL FKs;
dotted edges are application/natural-key associations, not enforced or source-stable identity guarantees.
Metadata and index definitions are in S01–S03. I03/R01/R04/R05 are bounded live join tests.

## Entity capability map

| Business entity | Actual representation | Identity/grain | Assessment |
|---|---|---|---|
| Venue | Implicit single venue; outlet/site fields raw | No venue key | Cannot scope source IDs, dates or future multi-venue analysis |
| Trading session/service | Date in sales; hardcoded reservation lunch/dinner split | No session entity | Calendar overlap only; trading policy not modelled |
| Sale/transaction | Some retained Lightspeed CSV Sale Number | No canonical entity | Current reports aggregates; no complete transaction coverage |
| Sale line | Empty `canonical_sale_item` placeholder | Lacks transaction/product FKs | Not a transaction-line model despite table name |
| Product/menu item | `product_name_key` in product-day facts | Normalised description | Source codes discarded; aliases/variants conflated |
| Stock/ingredient | `stock_code`/name on invoice lines | No master record | Partially identifiable purchases, no reliable ingredient ontology |
| Recipe | IDs/names in raw links and names/cost in raw sales | No canonical entity | Useful raw master references, no ingredient compositions |
| Recipe ingredient | None | — | Cannot compute ingredient-specific margin/consumption |
| Supplier | Name on canonical header; ID/code raw | Canonical name only | Source identity available but unmodelled |
| Purchase invoice | Populated canonical header | Source + invoice number | Two real cross-supplier collisions |
| Invoice line | Populated canonical line | Invoice number + ordinal | No header FK; reordering/removal identity risk |
| Stock movement | None | — | Purchases are not a complete movement ledger |
| Count/wastage | Empty canonical tables | Name/date fields | Model exists; no received source data |
| Employee | None; app user account is not employee master | — | No employee mapping |
| Shift/timesheet | Empty labour-entry and shift tables | Source ref / staff ref capacity | No Deputy records or parser |
| Reservation | Empty canonical reservation | Source reservation ID capacity | Import implementation exists, no real rows |
| Reservation status event | No separate event model | Version snapshot only | Cannot analyse lifecycle timing from current facts |

## What joins work now

1. **Canonical→raw→run:** enforced, no observed orphans (T01); useful single-evidence lineage.
2. **Invoice header→line by invoice number:** no current missing keys, but supplier identity is already
   collapsed. 1,167/1,167 headers have lines; 0 orphan keys. “Join succeeds” is not “join preserves business entities”.
3. **Supplier→purchases:** grouping by header supplier name is available through SQL and Kitchen API;
   stable-supplier analysis is conditional on resolving the two collision keys and aliases.
4. **Stock-code price history:** R05 finds **962 supplier/stock pairs**, **513 with purchases on multiple
   dates**, **183 with differing unit-cost candidates**. These can be inspected today, but UOM/pack comparability,
   zero costs, discount treatment and collision repair must precede asserting genuine price changes.
5. **CTB raw product→raw recipe links:** **502/502 stock codes overlap**, but 7/601 link codes map to multiple
   recipes. Preserve source/venue/code and effective/active mapping state; do not choose an arbitrary first match.
6. **Daily domain time alignment:** date joins can correlate daily summaries, where populated.
   Labour/reservations are empty; hour/service/person attribution is not currently possible.

## Where joins fail or become ambiguous

### Supplier and invoice identity

Source CTB has `invoiceId` + `supplierId`; CSV has SupplierCode. None participates in canonical key creation.
`CanonicalInvoiceService.java:31–33,67–68` creates logical identity from invoice number only;
`CtInvoiceCsvParser.java:48–62` groups by invoice number and takes first header fields.
Across received exports two number keys have multiple supplier names; AJAX independently confirms two
number keys with different supplier IDs (R01). Some historical changes could be corrections, but current
source collisions prove invoice number is not a global source-business identifier.

Header and line source refs likewise omit supplier and venue. A collided number can overwrite a header
and merge/overwrite ordinal lines, while unique constraints still pass. Therefore current duplicate-key
rate of zero understates the business identity defect. Quantified monetary impact per collision remains unknown.

### Line sequence and duplicate candidates

`CtInvoiceCsvIngestService.java:72–90` assigns incrementing ordinal per grouped invoice.
`CanonicalInvoiceLineService.java:38–49` supersedes only the supplied source fact, never retires missing ordinals.
Payload analysis finds one current line beyond its last parseable received CSV's line count; at least one
cross-export overlapping invoice changed line count. No same-multiset/different-order case was found among
**consecutive** export overlaps; this is not an exhaustive guarantee that reordering never occurred.
I03: 18 repeated identical-value groups, 79 excess line candidates. Source exports also contain repeated
lines; repeated invoice items can be legitimate. Investigate source IDs and header control totals rather than delete.

### Product and ingredient mapping

520 sale names vs 946 purchase names overlap at **one** normalised name. That is not a usable sales→ingredient join.
Menu items and bought ingredients are intentionally different entities; recipe compositions should bridge them.
Stock codes are present on 5,678/6,156 current lines, but 9/960 codes have multiple names and 18/946 names
have multiple codes. Namespace, supplier SKU, base-UOM and alias rules are required.

### Temporal relationships

No session or interval-overlap model aligns sale timestamps with staff timesheets or reservations.
OpenTable code splits before/after 15:00 in Australia/Sydney; CTB revenue uses Melbourne; frontend uses
browser-local dates on reservations/staff and UTC dates on Kitchen. Sydney/Melbourne currently share DST,
but this does not replace one venue calendar definition. Lightspeed reconciliation/opened dates differ.
Day joins cannot prove customer or staff causal attribution.

## Minimal additive foundations

Prioritise a source-scoped identity map and correct fact grain rather than building every possible entity at once:

1. `venue` + source/outlet mapping and a versioned trading/service calendar.
2. `supplier` + source-supplier aliases; `purchase_invoice` stable source ID and supplier FK;
   line→invoice FK, stable source-line ID where available, or documented versioned matching key.
3. `stock_item` + supplier SKU/UOM/pack conversions; `product` + POS/CTB source identifiers and aliases.
4. `recipe`, effective product→recipe links and ingredient-quantity/UOM facts **once actual source contract is verified**.
5. Atomic sale header/line/payment/event tables **only at demonstrated input grain**, alongside current daily facts.
6. Source employee/timesheet/reservation entities once received; interval/session bridges with explicit attribution policy.
7. Many-contributor and field-level lineage, including PDF raw IDs and parser version.

Existing canonical rows and UUID histories can remain. Introduce keys/bridge tables, backfill with confidence
and ambiguity flags, validate cardinalities, then make new analytical views authoritative. Do not rewrite
history by silently interpreting existing invoice numbers or normalised names as global IDs.
