# Goldy's product requirements

Approved scope baseline, 4 November 2026. Scope reflects the owner's decisions in the scope review;
delivery and operational acceptance are separate.

## Purpose and priority

Give owners and managers of the current pub trustworthy reports, useful forecasts
and explainable operational information without manually reconciling disconnected
systems. **Trustworthy weekly reporting and exports lead delivery.** Other accepted
capabilities remain in target scope, without implying equal priority.

The platform reads operational systems. It retains evidence, resolves disagreements,
and presents qualified results. Users can keep local notes, corrections and assigned
follow-ups. Ordering, rostering, payment execution and other source transactions
remain in external systems. Architectural safeguards remain in
[system context](system-context.md) and the backend invariant rules.

## Target scope

| Area | Required outcome |
|---|---|
| Reporting | Weekly reports and reusable custom reports; CSV and formatted Excel exports. Live views plus retained dated versions of issued/sent reports; corrections create new versions. |
| Sales and products | Explain sales, product mix, prices and adjustments from supported source evidence; make comparisons and qualifications explicit. |
| Forecasting | Overall revenue and individual-product sales forecasts, with evidence scope, uncertainty and comparison against actuals. |
| Stock and recipes | Purchases, dated counts, recorded wastage, recipe economics, usage and unexplained variance, when the required movements and units are supported. |
| Replenishment | Warn about shortages before the next delivery and suggest explainable quantities for human review; never place orders. |
| Cost and profit | Distinguish purchases, theoretical recipe costs, actual inventory-based COGS, gross margin and operating profit. Prime cost requires validated actual COGS and labour cost on comparable bases. Publish each only with supported meaning and inputs. |
| Supplier oversight | Explain orders, deliveries, invoices and credits; show payment status where evidence supports it. |
| Labour | Roster, hours and cost analysis. Wages and full employment cost are separate. Weekly-report inclusion awaits reliable data and validated meaning. |
| Reservations | Bookings and recorded reservation outcomes; booked party size is not independently verified attendance. |
| Reviews | Ratings, full text, themes and sentiment, traceable to source reviews. |
| Business interaction | Existing Ask Goldy's and saved-dashboard capabilities remain; reusable reports and operational workflows build on governed data rather than creating a second calculation path. |
| Follow-up | Local notes, correction proposals and operational actions assigned to people with completion tracking. Recording completion does not establish external execution. |
| Automation | Scheduled report delivery and condition-based alerts via notifications/exports, with authorized recipients and visible failures. |
| Access and UX | Owners and managers only. Keep the current calm visual style and desktop-first direction; improve their business workflows. Manager permissions remain undecided. |

Missing source data creates an explicit dependency, not invented facts or silent
removal of an accepted capability. Stakeholder policies, valuation methods,
thresholds and quality targets remain undecided unless separately confirmed.

## Boundaries

Out of active scope: second-pub planning and live multi-venue operation; staffing
recommendations; independent actual-attendance tracking; service-stage/timing
analysis; staff application access; payroll obligations, payments and settlement reporting. Safety, licence, competency and incident-control
features remain future candidates. Customer balances, deposits, vouchers, credits
and outstanding tabs remain undecided.

No write-back to connected source systems, generic AI SQL tool or executable
AI-generated UI. Existing append-only evidence, bitemporal history, permission and
bounded-tool safeguards remain. Owner/manager product scope grants nobody access
by itself; future manager grants require an explicit mapping.

## Acceptance and traceability

The [SRS](srs.md) defines required behaviours and points to detailed criteria using
stable IDs. [App state](app-state.md) distinguishes code capability from verified
production behaviour. [Issue index](roadmap.md) track gaps, dependencies and decisions.

Judge reporting against a representative completed week, independent source
comparisons, visible qualifications, authorized exports and reproducible issued
versions. Measure report assembly/checking effort against an observed baseline.
Do not invent universal accuracy, completeness or performance targets to close
an acceptance gate.
