# Follow-up to PR 231 at e25a6abf

The retained 26 October–1 November report is now usable **locally with the reviewed decisions**.
Production has the ten additional exact wastage mappings, but does not have the new CTB_PRODUCT
scope/report/editor implementation. No merge or deployment is authorized or performed.

## What is applied and what is prepared

- Applied through the authenticated OWNER application API: ten WASTE_LINE revisions effective
  26 October. Reread 99 revisions, including every prior 89 unchanged, and 107 classified waste
  lines. Food 24.8493 + beverage 535.8560 + unknown 0 = recorded total 560.7053.
  `applied-waste-mappings.json` records the exact decisions and verification.
- Prepared, not applied: 194 CTB_PRODUCT revisions for exact native site-0/code identities,
  effective 26 October. The fixture `reporting-source-decisions-2026-10-26.json` is the exact
  review payload; `reviewed-sales-evidence.json` retains the ranked real source context for each
  decision (POS category, qualified identifiers, recipe links/folders/ingredients where observed,
  raw references and dates). This is an explicit reviewed list, not approval of model proposals.
- Owner confirmed dog treats are OTHER. The two source policies retain this explicit reason.
  Stock buckets, item roles, café membership and lifecycle are UNKNOWN; no historical café
  record, taxonomy correction, manual porterhouse assignment or prior mapping was removed.

## Identity treatment and editor race

Raw CTB revenue payloads contain native site -1 on 28 July and site 0 on 29 July. The parser
passes this value through; it does not synthesize the change. The full observed ranges are
25–28 July (-1) and 29 July–2 November (0). Timing suggests a boundary but proves neither
identity equivalence nor the business reason. No cross-site equivalence was introduced.

Name-level ambiguity remains held. The new source scope uses the already observed qualified
identity `site:<siteNumber>:code:<length>:<stockCode>`. Every contributing source row must have
an applicable mapping with the same nature, and gross/quantity must exactly match the selected
CTB product/day. Partial, mixed or UNKNOWN policies hold the entire alias, overriding name
fallback. Another selected source cannot borrow a CTB policy. Missing tax and unknown site
still withhold tax basis. All prior dated mapping precedence remains intact.

The editor disables exact-line, recipe and history buttons while busy, invalidates responses
against the draft revision, and displays the exact identity in the approval preview. The
regression uses a deferred preview, attempts every identity selector while pending, verifies
that A stays selected, then changes to B and verifies that A cannot be approved.

## Before and after the reviewed source decisions

Amounts are AUD ex GST. Coverage uses the existing absolute eligible value denominator,
excluding reviewed OTHER, with the **95% gate unchanged**. The report artifacts come from
PostgreSQL-backed real-week integration tests using the retained 1,034 source rows.
| Date | Before coverage | After coverage | Food net | Beverage net | OTHER | Unknown |
|---|---:|---:|---:|---:|---:|---:|
| 2026-10-26 | 24.10% | 99.94% | 4916.8176 | 5741.8300 | 4.1300 | 6.9300 |
| 2026-10-27 | 19.81% | 99.88% | 3623.7800 | 4175.9500 | 6.3700 | 9.2400 |
| 2026-10-28 | 41.83% | 99.99% | 8084.2200 | 10065.5300 | 17.3600 | 2.3100 |
| 2026-10-29 | 26.26% | 99.96% | 4605.6874 | 8180.4700 | 365.0300 | 4.6200 |
| 2026-10-30 | 21.91% | 99.97% | 8905.6619 | 24282.0000 | 6.3600 | 11.5500 |
| 2026-10-31 | 20.48% | 99.94% | 10698.9094 | 27054.9082 | 16.9000 | 20.7900 |
| 2026-11-01 | 28.37% | 99.87% | 7607.7063 | 13064.1611 | 20.9200 | 27.2700 |

Weekly totals: food **48,442.7826**, beverage **92,564.8493**, OTHER **437.0700**, unknown **82.7100**;
sum **141,527.4119**, unchanged. Weekly coverage 99.941378%. Food/beverage actuals are available
on all seven dates. Against retained weekly targets 45,600/74,400, differences are
2,842.7826 / 18,164.8493. Weekly recorded-wastage rates are 0.051296% food and 0.578898% beverage,
using the seven matching category-sales dates. All remain QUALIFIED, not certified complete.
Daily target differences remain absent because targets are weekly. No wastage was observed on
31 October, so that date's wastage/rate stays null rather than becoming zero.

The remaining positive unknowns are Dill Aioli 71.61 (only uncategorised POS evidence and
invalid recipe links), Kids Orange Smiles 9.25 (ambiguous multiple categories, no valid recipe),
and Gluten Free Bread 1.85 (uncategorised, no valid recipe). Exact site/code source observations
remain visible. Next evidence is a current POS menu/category or CTB sale-to-recipe definition
for those exact identities. Zero-valued unreviewed modifiers also remain unknown; their value
is observed zero and they do not hide positive exposure. Earlier café membership/closure dates
still need an owner/source roster or reliable outlet identifiers, independently of nature.

## Wastage evidence and qualifications

The ten unknown lines were reviewed individually. Five food lines are gnocchi, chicken fillet
and chips with exact waste references and food suppliers/pack units. Four wine lines have
exact inventory names, wine suppliers and 750 ml pack observations. The cocktail line explicitly
references recipe 733; the CTB recipe catalogue identifies 733 as Resting Witch Face and POS
corroborates COCKTAILS. Its detailed recipe is absent; no similar sale name identifies inventory.
The decisions are exact-line policies, preserving recipe precedence and UNKNOWN withdrawals.

Values remain retained quantity × source unit cost rounded to four decimals, without converting
pack units or replacing recorded costs with current recipe costs. All reasons, including KO,
burned and missing reasons, remain included. Existing source-header evidence covers all eleven
completed-week wastage records: ten exact; record 161 differs by -0.0019666667 (retained
12.1343 versus source 12.1362666667). Fresh exact detail 822 proves the precision difference: raw quantity
0.083333333333333329 × 59 = 4.916666666666666411; retained quantity 0.0833 × 59 = 4.9147.
The 0.001966666666666411 difference accounts for the header residual within source decimal
precision. No quantity conversion, cost replacement or retrospective schema rewrite was applied. Tax basis is not stated by these source detail/header fields; recipe GST assumptions
do not certify wastage cost tax treatment. Exact next evidence: CTB's definition/export of
historical wastage `cost`, stating GST inclusion and quantity/pack precision. Rates retain this
qualification. Missing source observations are not represented as complete inventory loss.

## Headline discrepancy traced by selected source

Fresh read-only ledger queries reproduce the existing product and headline observations:

| Date | Product net CTB | Selected headline net | Headline source | Residual | Receipt final net |
|---|---:|---:|---|---:|---:|
| 26 Oct | 10669.7076 | 10669.7076 | CTB | 0 | 10676.45 |
| 27 Oct | 7815.3400 | 7815.3400 | CTB | 0 | 7809.16 |
| 28 Oct | 18169.4200 | 18213.6100 | LIGHTSPEED | 44.1900 | 18269.42 |
| 29 Oct | 13155.8074 | 13197.9300 | LIGHTSPEED | 42.1226 | 13161.88 |
| 30 Oct | 33205.5719 | 33205.5719 | CTB | 0 | 33283.84 |
| 31 Oct | 37791.5076 | 37791.5076 | CTB | 0 | 38077.35 |
| 1 Nov | 20720.0574 | 21238.5300 | LIGHTSPEED | 518.4726 | 21238.53 |
| Total | 141527.4119 | 142132.1971 | mixed | **604.7852** | **142516.63** |

The 9,405 receipt finalExTax observations independently reproduce the retained closed-date
Lightspeed report exactly. Receipt minus product is 989.2181; receipt minus selected headline
is 384.4329. This is not a food/beverage allocation residual.

The old Lightspeed scheduled report groups cash-up reconciliation dates while filtering
`revenue.sale_date` to one day, with a 5,000-row limit, Australia/Sydney. There is no canonical
Lightspeed daily row for 30 October and the old 31 October row is only 574.11 net. The newer
1 November report selects revenue.sale_date, site 96181 and explicit ex/inc-tax fields, has no
row limit, uses Australia/Melbourne and agrees with receipts. This is concrete source-scope
and date-basis evidence; it does not prove how every sale moves between days. All 9,405 sampled
receipt closed-time fields are absent, so a cutoff/hour reallocation cannot be verified.

Receipt adjustments already provide grossExTax/finalExTax plus separate Discount/Surcharge
amounts. CTB's selected source `amount` equals `discounted_amount` throughout the 1,034-row sample.
Subtracting discounts again would double-count them. Receipt evidence includes seven negative
lines (-84.65 gross); the product sample has no negative rows. Cash-out lines total 228 net
and appear in receipts (100 on 28 October), demonstrating a scope difference. Other examples
are fish-tostada receipt labels versus tuna-tostada CTB labels and receipt-only fish burger/
fisherman's basket on 30 October. These observations are not silently identity-matched or
allocated to close the gap. Product-level source/date deltas, tax, and final adjustment details
are retained in the adjacent SQL/CSV evidence.

Next action for a certified common headline: obtain/re-ingest a source-approved closed-date,
site-96181 report for all seven dates, retain its exact report filters, compare item identities
and cash-out/refund scope, then review the existing daily reconciliation selection. No headline
rule, canonical observation, ingestion schedule or residual was changed in this task.

## Release dependency

Merge/deploy PR 231 with V109/V110 only after explicit authorization, then apply the prepared
194 reviewed source decisions through the owner API and reread the report and revision history.
Until then, production sales coverage remains at the baseline; only the additional ten wastage
revisions are live. Local before/after JSON is evidence of the implementation, not a production
smoke test. See `validation.md` for checks actually run and release state.
