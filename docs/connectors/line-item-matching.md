# Line-Item Matching (discovery)

How the two sources' *line items* line up, measured against a real paired sample
(14–20 Sep 2026). This is the discovery reference for the line-item reconciliation
slice — it records what the data actually is, so the matching key can be designed
from evidence rather than guessed.

## Data sources

| Source | Pull | Shape |
|---|---|---|
| CTB sale items | `POST Sale/SearchSaleItemsByDateRange` (`fromDate`,`toDate`,`searchType=-1`) | per `stockCode`: `stockDescription`, `quantitySold`, `amount` (incl. tax), `taxAmount`, `unitPrice`, `recipeName` |
| Lightspeed "Sales By" | `/report/salesummarybyproduct` → `#btnReportExport` CSV | per product: `Product` (name), `Quantity`, `Sale Amount`, `Cost` |

Note: the Lightspeed "Sales By" CSV has an **empty `Product Number` column** — there
is no SKU in the export, only the product *name*. So the matching key is the name,
not a code. CTB's `SearchSaleItemsByDateRange` returns only the per-product aggregate
(`saleDate`/`parentStockCode`/`childGroupData` are null for every `searchType` tried),
so modifier linkage is not exposed by the API.

## Result (14–20 Sep 2026 sample)

| | count | amount |
|---|---|---|
| Lightspeed products | 303 | $169,470.53 |
| CTB products | 285 | $168,219.02 |
| **Matched (name + "Kids" alias)** | **285** | |
| Matched residual (LS − CTB, summed) | — | **$105.67** (~0.06%) |
| Truly Lightspeed-only | 17 | $1,145.84 |
| **Reconciled** | | **$1,251.51 = $105.67 + $1,145.84** (exactly the daily diff) |

For the vast majority of matched products, `Quantity` and `Sale Amount` agree **to the
cent** (`Pint - Carlton Draught`: 1232 / $17,340.98 on both sides). The $105.67 residual
is spread across 39 products, the largest being `Garlic Aioli` (LS 150 vs CTB 127,
$58.42) — a handful of items where CTB's per-product rollup differs slightly.

## The mismatches

**1. Renames** — the same product under a different name (resolved by an alias key that
strips a leading `New `):

`Kids Fish & Chippies` → `New Kids Fish & Chippies`, `Kids Gnocchi Bolognese` →
`New Kids Gnocchi Bolognese`, `Kids Gnocchi Napoli`, `Kids Orange Smiles`,
`Kids Parma`, `Kids Schnitty`, `Kids slider`, `Kids Teddy Bears Picnic`.

**2. Truly Lightspeed-only (17)** — modifiers/sides that CTB does **not** track as
standalone line items (their amounts are simply absent from CTB, which the exact
reconciliation confirms — they are *not* folded into a parent, because matched parents
already agree to the cent):

`Fish Burger`, `Burger`, `Cash Out`, `Fisherman's Basket`, `Large Chips`,
`Chicken Burger`, `Gnocchi - Mushroom`, `Dill Aioli`, `Home-made Potato Cake`,
`Fish Fillet`, `Small Chips`, `Parmesan`, `Kids Gnocchi`, `Calamari Rings`,
`Thai Sweet & Sour Wings`, `Tartare`, `Grilled`.

## Recommended matching strategy

1. **Key = normalized product name** (lower, strip non-alphanumerics, collapse
   whitespace) with a **`New `-prefix alias** for the rename cases. No SKU exists on
   the Lightspeed side.
2. **Match at product level** — one CTB `sale_items` row (summed per `stockCode`) vs
   one Lightspeed "Sales By" row.
3. **Tolerance = zero** for the core: matched products agree to the cent, so reconcile
   `quantitySold` and `amount` on exact equality and surface any non-zero residual
   (the 39 products behind the $105.67) as small day-level/item-level diffs to inspect.
4. **Surface the 17 Lightspeed-only items explicitly** as "no data from CTB" (the same
   "no data from source X" pattern as the daily slice), never silently dropped — they
   are the ~$1.1k/week of modifier sales CTB's Kounta integration does not ingest.

## Conclusion

The line-item slice can be implemented without further gating: the key is the
normalized name (plus a small alias), the tolerance is zero, and the only unmatched
class is the 17 modifier/side items, which are surfaced explicitly. No per-transaction
or SKU data is required.
