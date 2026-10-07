# Goldy's Metric Catalogue

Every metric below is the single, authoritative definition of that number. Two parts of Goldy's
must never compute the same named metric differently. Base metrics read resolved projections;
derived metrics are formulas over base metrics. All results carry provenance and a notice when
data is unresolved ("display + note": a figure always shows when any resolved data exists).

| ID | Name | Definition | Formula | Unit | Source | Dimensions | Grains | Permission | Missing-data |
|---|---|---|---|---|---|---|---|---|---|
| `sales.gross` | Gross sales | Resolved gross sales incl. GST | totalSales | AUD | resolved_daily_sales | — | day, week, month | reconciliation.sales | displays resolved sum; unresolved days noted |
| `sales.net` | Net sales | Resolved net sales = gross − GST | netTotal | AUD | resolved_daily_sales | — | day, week, month | reconciliation.sales | displays resolved sum; unresolved days noted |
| `sales.gst` | GST | Resolved GST | gstTotal | AUD | resolved_daily_sales | — | day, week, month | reconciliation.sales | displays resolved sum; unresolved days noted |
| `reservations.bookings` | Bookings | Resolved bookings | bookings | count | resolved_reservation_day | service_period | day, week, month | reservations.metrics | sum (absent day = no row, not zero, unless projected) |
| `reservations.attended` | Attended parties | Resolved attended | attended | count | resolved_reservation_day | service_period | day, week, month | reservations.metrics | sum |
| `reservations.covers` | Covers | Resolved covers (guests) | covers | count | resolved_reservation_day | service_period | day, week, month | reservations.metrics | sum |
| `reservations.no_shows` | No-shows | Resolved no-shows | noShows | count | resolved_reservation_day | service_period | day, week, month | reservations.metrics | sum |
| `labour.scheduled_hours` | Scheduled hours | Resolved scheduled hours | scheduledHours | hours | resolved_labour_day | department | day, week, month | labour.hours | sum of non-null |
| `labour.actual_hours` | Actual hours | Resolved actual hours | actualHours | hours | resolved_labour_day | department | day, week, month | labour.hours | sum of non-null |
| `labour.cost` | Labour cost | Resolved actual cost | actualCost | AUD | resolved_labour_day | department | day, week, month | labour.cost | displays resolved sum; unknown-cost days noted |
| `inventory.purchases` | Purchases (COGS) | Resolved purchases | purchases | AUD | resolved_inventory_day | — | day, week, month | inventory.cost | sum of non-null |
| `inventory.wastage` | Wastage | Resolved wastage | wastage | AUD | resolved_inventory_day | — | day, week, month | inventory.cost | null when no wastage data at all |
| `inventory.stock_on_hand` | Closing stock | Resolved stock-on-hand | stockOnHand | AUD | resolved_inventory_day | — | day | inventory.cost | latest value |
| `product.sales_amount` | Product sales amount | Resolved product amount | amount | AUD | resolved_product_sales | product | day, week, month | reconciliation.sales | sum |
| `product.sales_quantity` | Product sales quantity | Resolved product quantity | quantitySold | units | resolved_product_sales | product | day, week, month | reconciliation.sales | sum |
| `reservations.no_show_rate` | No-show rate | no_shows ÷ bookings | no_shows ÷ bookings | % | derived | — | day, week, month | reservations.metrics | null when bookings = 0 |
| `reservations.booking_to_cover_conversion` | Booking-to-cover conversion | attended ÷ bookings | attended ÷ bookings | % | derived | — | day, week, month | reservations.metrics | null when bookings = 0 |
| `reservations.avg_party_size` | Average party size | covers ÷ attended | covers ÷ attended | ratio | derived | — | day, week, month | reservations.metrics | null when attended = 0 |
| `sales.average_spend_per_cover` | Average spend per cover | sales.gross ÷ reservations.covers | sales.gross ÷ reservations.covers | AUD | derived | — | day, week, month | reconciliation.sales | null when covers = 0 or gross unresolved |
| `labour.hours_per_cover` | Hours per cover | labour.actual_hours ÷ reservations.covers | labour.actual_hours ÷ reservations.covers | hours/cover | derived | — | day, week, month | labour.hours | null when covers = 0 |
| `labour.cost_per_cover` | Cost per cover | labour.cost ÷ reservations.covers | labour.cost ÷ reservations.covers | AUD/cover | derived | — | day, week, month | labour.cost | null when covers = 0 or cost unknown |
| `labour.hours_variance` | Hours variance | labour.scheduled_hours − labour.actual_hours | labour.scheduled_hours − labour.actual_hours | hours | derived | — | day, week, month | labour.hours | — |
| `labour.foh_percent` | FOH labour cost % | FOH labour.cost ÷ sales.gross | FOH labour.cost ÷ sales.gross | % | derived | — | day, week, month | labour.cost | null when gross = 0/unresolved or FOH cost unknown |
| `labour.boh_percent` | BOH labour cost % | BOH labour.cost ÷ sales.gross | BOH labour.cost ÷ sales.gross | % | derived | — | day, week, month | labour.cost | null when gross = 0/unresolved or BOH cost unknown |
| `inventory.food_cost_percent` | Food cost % | inventory.purchases ÷ sales.gross | inventory.purchases ÷ sales.gross | % | derived | — | day, week, month | inventory.cost | null when gross = 0/unresolved or purchases unknown |
| `product.top_sellers` | Top sellers | ranked product list by summed resolved amount | ranked product list by summed resolved amount | list | derived | — | day, week, month | reconciliation.sales | items with unresolved days flagged, summing resolved rows only |

## What each metric means (spec §3)

Every metric is registered under a stable `MetricId` with a `MetricDefinition` contract. IDs are
stable, machine-readable, dotted strings — never physical table or Java class names. A metric also
carries persistent caveats (the `notes` field, e.g. "gross includes GST") that always render with
the metric, distinct from the runtime notices on a result, which describe data conditions for a
specific query (e.g. "2 days unresolved").

### Base metrics (spec §3.1)

Base metrics read resolved projections. They are live today and are the authoritative
post-reconciliation value: `sales.gross` / `sales.net` *are* the reconciled figure.

- `sales.gross`, `sales.net`, `sales.gst` — `resolved_daily_sales`, no dimensions, day/week/month.
- `reservations.bookings`, `reservations.attended`, `reservations.covers`,
  `reservations.no_shows` — `resolved_reservation_day`, dimension `service_period`, day/week/month.
- `labour.scheduled_hours`, `labour.actual_hours`, `labour.cost` — `resolved_labour_day`,
  dimension `department`, day/week/month.
- `inventory.purchases`, `inventory.wastage` — `resolved_inventory_day`, no dimensions,
  day/week/month; `inventory.stock_on_hand` is day-only (latest value).
- `product.sales_amount`, `product.sales_quantity` — `resolved_product_sales`, dimension
  `product`, day/week/month.

### Derived metrics (spec §3.2)

Derived metrics are compositions of other metric results in code (a plain Java function, not a
parsed formula language). They declare no dimensions; a zero or unknown denominator yields `null`
with a notice ("no covers data" / "denominator is zero"), because no value exists.

## Display + note semantics (spec §3.1)

A metric **always displays a value when any resolved data exists**, and attaches a **notice**
explaining what is going on. There is no "hide the number" mode:

- **Partial data** (some days unresolved): the value is the sum of the resolved constituents; the
  unresolved dates are recorded in `MetricProvenance.missingPeriods` and surfaced as a notice
  ("2 days unresolved"). A day is never silently zeroed — it is summed-excluded *and* flagged.
- **No resolved data at all**: the value is `null` (nothing to display) plus a "no resolved data"
  notice.
- **Derived metrics**: a zero/unknown denominator yields `null` with a notice ("no covers data" /
  "denominator is zero"), because no value exists; a partial numerator still displays with a notice.

This keeps every figure on screen with an explanation, rather than a blank tile, while still never
treating an unknown value as zero.

## Resolving ambiguity (spec §2)

| Ambiguous term | Resolution |
|---|---|
| **net sales** | `sales.net` — resolved net sales = gross − GST (`netTotal`). AUD. |
| **reconciled sales** | Synonym for **resolved** sales — the authoritative post-reconciliation value. `sales.gross`/`sales.net` *are* the reconciled figure. One name. |
| **POS-only sales** | **Not a business metric.** Lightspeed's raw unreconciled figure, already surfaced on the Sales screen as per-source *provenance*. Catalogue rule: business metrics are always resolved; single-source raw figures are reconciliation provenance. |
| **sales excluding refunds** | `sales.excluding_refunds` — **defined but deferred** (refunds not ingested; see §11). |
| **trading-day vs calendar-day sales** | Not distinct metric IDs — a *time-semantics* parameter. Sales metrics are queried with a `Calendar ∈ {TRADING, CALENDAR}`. Today both resolve to "calendar day in venue timezone"; the exact boundary is a deferred business decision (§11). |

## Persistent notes and version

All catalogue entries are at definition `version` `1`. The only persistent metric note carried
today is on `sales.gross`: **"gross includes GST"**.

## Deliberately deferred (spec §11)

- `sales.excluding_refunds` — refunds are not ingested, so no `MetricId` exists yet.
- `budget` / `forecast` comparisons — no data source.
- Exact trading-day boundary — defaults to calendar-day-in-venue-timezone until a business
  decision.
- Arbitrary department/labour dimension split — FOH/BOH are distinct metric IDs today; a free
  department dimension is a future extension, not this pass.
- Grafana-style dashboard variables — future extension; the query/panel model accommodates it
  without a rewrite.
