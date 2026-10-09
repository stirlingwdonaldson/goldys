# 07 — Versioned canonical facts, reconciliation and read-model correctness

## Independently verified results

| Probe | Population checked | Result |
|---|---:|---|
| R02 latest CTB raw revenue → current canonical | 374 source rows / 187 days | 0 missing keys, 0 exact monetary differences |
| I05 current invoice-line sums → inventory projection | 164 purchase dates | 0 key/value differences; all wastage/stock null |
| I05 CTB current product facts → resolved | 13,185 product-days | 0 key/amount/quantity differences |
| I05 resolved daily sales → selected canonical source | 187 dates | 0 absent source facts, 0 gross/net/GST differences |
| I08 representative purchase dates Oct 2/5/8 | 42 / 56 / 10 lines | Projection deltas zero |
| T01 four populated versioned domains | 31,684 versions | 0 broken system-time chains, 0 invalid system intervals, 0 raw orphans |

These validate **the inspected snapshot and selected arithmetic**, not all parser/source semantics,
future concurrency behaviour or external business completeness. Resolved purchasing correctly reproduces
canonical lines even when those lines have invoice collisions, stale tails or header mismatches.

## Sales reconciliation in production

Current resolved states: **179 single CTB**, **6 rule-selected CTB**, **2 Lightspeed overrides** (I05).
Eight dates have both sources, and all eight differ above one cent (T03); none currently has `has_conflict=true`.
Active custom rule chooses a source; two overrides apply Oct 5/6. Four retained rule versions, three superseded,
show rule history is retained. Earlier priority versions used display names rather than source IDs; those are superseded.

`DailySalesResolver.java:27–57` implements override → single/agreement → rule → unresolved.
`hasConflict()` is true only for unresolved “conflict”, so a rule/override removes the open exception even
though source disagreement remains. This is a definition, not disappearance of the source values.
“No open conflicts” must not be interpreted as “all sources agree”.

Agreement compares **gross only** (`DailySalesResolver:64–70`), then selects GST/net from the first source.
Potentially equal gross with different GST/net is treated as agreement; no current agreed multi-source row
exists to measure this risk. Field-level agreement and deterministic authority should be explicit.

## Bitemporal audit: system history works; valid time is not independently modelled

`BitemporalEntity.java:13–16` describes independent axes. Production T01 instead finds:

- Daily 203/203, invoice 2,333/2,333, lines 15,876/15,876, products 13,272/13,272
  have **valid_from = recorded_at**.
- **Every valid_to is null**, including superseded rows.
- Current identity uniqueness and version closures work in the inspected system-time chains.

Services pass ingestion-record time for both axes (`CanonicalDailySalesService:57–68`,
`CanonicalInvoiceService:44–64`, `CanonicalInvoiceLineService:51–70`). `supersede` closes only system time
(`BitemporalEntity:70–78`). Old business dates imported in October therefore have valid_from in October.

**Verdict (F12):** this is usable transaction-time revision history with separate business-date fields,
not verified business-effective bitemporal history. SQL “what did we know at time T?” is possible:

```sql
SELECT count(*)
FROM public.canonical_invoice_line
WHERE recorded_at <= timestamptz '2026-10-08 12:00:00+00'
  AND (superseded_at IS NULL OR superseded_at > timestamptz '2026-10-08 12:00:00+00');
```

Filter `invoice_date` separately for the business period. A valid-time query alone overcounts overlapping
versions and excludes earlier business periods until the import date. No generic historical/as-of API exists
(`BitemporalRepository.java:7–14`). Raw immutable values and version chains are a strong base for additive correction.

## Replay/rebuild semantics

- Resolved current tables are replaceable read models, with PKs at their intended grain.
- Daily/product projections and listeners recompute from current canonical and rules/overrides.
  Same-transaction events make errors roll back the affected canonical write; prior push-run SUCCESS still persists.
- Inventory `recomputeAll` derives dates from current invoice lines and rebuilds sums
  (`InventoryProjector.java:43–90`); null wastage/stock is deliberate.
- A row's date correction publishes only the **new** invoice date (`CanonicalInvoiceLineService:31–33`),
  potentially leaving old date projection stale until rebuild. T07 observes one line identity with historical
  date changes; current full sums match (recent startup rebuild has repaired/avoided a current discrepancy).
  This is a code/event risk, not a currently observed wrong inventory projection.
- Full rebuild does not replay source parsing or resolve identity collisions. PDF fields do not affect the
  purchases sum but cannot be regenerated exactly without stored extraction output/versioning.
- Source-deleted/shrunken CSV lines are not retired; canonical “current” can include obsolete sequence tails.

## Traceability limits

Raw foreign keys make each version traceable to **one** retained payload, but are not a many-input lineage graph.
Daily aggregation stores first raw ID per date; product aggregation stores first contributing raw ID.
Latest revenue days did not cross page boundaries, so no observed missing-page contributor at that grain;
the general schema cannot represent multiple contributing pages or raw record positions.

PDF enrichments deliberately retain CSV raw IDs. No field→PDF FK/extractor-version/confidence exists.
Raw PDFs cannot be traversed from a measurement reliably without independently reconstructing filename associations.
Resolved tables do not store canonical contributor IDs/rule IDs. Gross/net/GST provenance drilldown reconstructs
current source values dynamically (`TrustService.java:107–165`); other metric drilldowns throw unsupported.
Metric executors often put `Instant.EPOCH`/empty raw IDs in provenance; derived example
`DerivedMetricExecutor.java:220–231`. Current trust wrapper enriches summaries, not complete atomic lineage.

## Missingness and metric confidence

`GrainAggregator.java:24–57` produces partial bucket sums if any resolved day exists. It records missingDays,
but output point itself has no incomplete status when some days are missing. Derived ratios discard base
missing-day notices and may compute a non-null ratio of partially observed sums
(`DerivedMetricExecutor:102–139`). Inventory/Labour summaries sum non-null days and omit completeness/trust
from summary DTOs (`InventoryReportingService:69–91`, `LabourReportingService:79–92`).

Purchase dates are sparse events; absent dates can mean legitimate no purchase **or** missing extraction.
The platform needs dataset/window completion manifests and venue calendar expectations to tell them apart.
Do not zero-fill unknown days or call partial totals complete. Current SQL arithmetic alone cannot settle that.
