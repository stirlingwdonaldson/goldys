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
not a code.

## Result (14–20 Sep 2026 sample)

| | count |
|---|---|
| Lightspeed products | 303 |
| CTB products (distinct name) | 285 |
| **Exact normalized-name matches** | **278 (92%)** |
| Renames ("Kids X" vs "New Kids X") | 8 |
| Lightspeed-only, no CTB counterpart | 17 |

For matched products, `Quantity` and `Sale Amount` agree **to the cent** — e.g.
`Pint - Carlton Draught`: 1232 / $17,340.98 on both sides.

## The mismatches

**1. Renames** — the same product under a different name:

- `Kids Fish & Chippies` → `New Kids Fish & Chippies`
- `Kids Gnocchi Bolognese` → `New Kids Gnocchi Bolognese`
- `Kids Gnocchi Napoli` → `New Kids Gnocchi Napoli`
- `Kids Orange Smiles` → `New Kids Orange Smiles`
- `Kids Parma` → `New Kids Parma`
- `Kids Schnitty` → `New Kids Schnitty`
- `Kids slider` → `New Kids slider`
- `Kids Teddy Bears Picnic` → `New Kids Teddy Bears Picnic`

(plus `Kids Gnocchi`, which CTB appears to have dropped or folded away.)

**2. Lightspeed-only items (17)** — likely modifiers/sides that CTB folds into a
parent product's amount rather than tracking as standalone line items:

`Burger`, `Calamari Rings`, `Cash Out`, `Chicken Burger`, `Dill Aioli`,
`Fish Burger`, `Fish Fillet`, `Fisherman's Basket`, `Gnocchi - Mushroom`,
`Grilled`, `Home-made Potato Cake`, `Large Chips`, `Parmesan`, `Small Chips`,
`Tartare`, `Thai Sweet & Sour Wings`.

There were **no CTB-only names** beyond the `New Kids` renames.

## Recommended matching strategy

1. **Key = normalized product name** (`lower` + strip non-alphanumerics + collapse
   whitespace). No SKU exists on the Lightspeed side.
2. **Alias the renames** — a small static map (`Kids …` ↔ `New Kids …`) or a
   `stripLeading("new ")` fallback on the CTB side.
3. **Tolerance = zero** — matched `quantitySold` and `amount` already agree to the
   cent, so there is no tolerance to guess; reconcile on exact equality.
4. **The 17 modifiers/sides** need one further probe (are they CTB modifiers on a
   parent item, or genuinely absent?) before deciding whether to drop, aggregate,
   or match them as modifier-level rows.

## Open question (gated)

Before implementing, confirm the 17 Lightspeed-only items against CTB's *modifier*
data (e.g. `childGroupData` / `parentStockCode` on `sale_items`) to see whether they
are folded into a parent. That determines whether the slice matches at product level
only, or product + modifier.
