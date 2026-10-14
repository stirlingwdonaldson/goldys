# Domain readiness assessment

Each dataset is placed on a six-tier ladder. "Readiness" here means *query-ready at
the original source grain with stable identity* — Labour, Wastage and Reports are
audited but excluded from that bar (recorded separately, as agreed).

| Tier | Meaning |
|---|---|
| T0 | Source never received (no raw bytes; code contract only) |
| T1 | Received, unparsed / un-canonicalized |
| T2 | Parsed but fields omitted from canonical storage |
| T3 | Canonicalized, not projected / no query interface |
| T4 | Projected/queryable but identity or enrichment gaps |
| T5 | Fully query-ready at source grain |

Numbers are live production counts (read-only probes, 2026-10-14) plus code-derived
schema facts. Superseded versions excluded where relevant.

| Source | Dataset | Canonical (current) | Resolved | Tier | Notes |
|---|---|---|---|---|---|
| LS | daily_sales | 1,675 | 1,944 | **T4** | query-ready at daily grain; no hour/service grain; 475 absent calendar dates; valid-time not implemented |
| LS | payments | 976,199 | 7,181 | **T5** | tender×day grain, PaymentMetricsQuery |
| LS | sale_items | 1,462,332 | 19,053 | **T4** | line-level; SKU 99.97% null (DF-09) |
| LS | deleted_sales | 3,207 | 1,009 | **T5** | DeletedSaleMetricsQuery |
| LS | products (report) | — | — | **T0** | 0 raw |
| LS | product_catalog | 0 | — | **T0** | 0 raw |
| LS | zreport | — | — | **T0** | 0 raw |
| CTB | daily_revenue | 191 | (shared) | **T4** | aggregated; department/outlet/revenue id dropped |
| CTB | product_sales | 13,849 | 13,849 | **T4** | stock code + cost/tax/discount dropped (DF-04) |
| CTB | invoices (CSV) | 1,200 hdr / 6,291 line | 169 | **T4** | 97 header/line mismatches; category/account/tax null; PDF enrichment sparse |
| CTB | invoices (AJAX) | — | — | **T1** | 66 raw, 0 canonical (raw-only by design) |
| CTB | invoice PDFs | — | — | **T2** | 2,687 PDFs enrich only 24 linked invoices |
| CTB | recipe_cost | 663 | 663 | **T5** | RecipeMetricsQuery |
| CTB | recipe_ingredients | 2,630 | 2,630 | **T5** | RecipeIngredientMetricsQuery |
| CTB | sale_recipe_links | 645 | — | **T3** | no resolved projection or query |
| CTB | stocktakes / stock_count | 0 / 0 | — | **T1** | 3 + 29 raw records, 0 canonical (DF-03) |
| CTB | wastage | 0 | — | **T1** *(excluded)* | 119 raw, 0 canonical |
| CTB | stocks / suppliers / stock-orders / statements / reference | — | — | **T1** | 21/3/9/3/18 raw, 0 canonical |
| OT | reservations | 24,194 | 1,424 | **T5** | ReservationMetricsQuery; guest linkage null |
| OT | reviews | 1,218 | (classification) | **T5** *(excluded — reports)* | ReviewMetricsQuery |
| OT | guests | 172 | — | **T3** | no query; derived from reviews |
| OT | menu_items | — | — | **T1** | 21 raw, 0 canonical |
| DEP | timesheets | 0 | 0 | **T0** *(excluded — labour)* | no raw, no parser |

## Excluded domains (status recorded, not held to readiness)

- **Labour (Deputy):** `canonical_labour_entry` and `canonical_shift` are empty
  (0 rows). No Deputy raw payload, no effective drop token, no parser. Requires
  OAuth registration + a first real delivery before any modelling can be validated.
- **Wastage (CTB):** `ctb-wastage-detail` has 119 retained raw records but 0
  canonical rows (DF-03) — received, un-canonicalized.
- **Reports (OpenTable reviews/reputation):** 1,218 canonical reviews,
  classification runs present (3 runs), `ReviewMetricsQuery` implemented in the
  classification package. This domain is functionally complete; it is excluded
  from the readiness bar only as agreed.

## Headline

- **T5 (fully query-ready):** payments, deleted-sales, reservations, recipes,
  recipe-ingredients.
- **T4 (queryable, with identity/enrichment gaps):** daily sales, product sales,
  invoices, sale items.
- **T3 (canonicalized, not queryable):** sale→recipe links, guests.
- **T1 (received, unparsed):** stocktakes/counts, stocks/suppliers/reference
  masters, invoice AJAX, menu items.
- **T0 (never received):** Deputy, Lightspeed products/catalog/zreport.
