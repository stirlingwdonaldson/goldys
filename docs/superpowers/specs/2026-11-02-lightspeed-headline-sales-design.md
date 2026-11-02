# Lightspeed headline sales correction

The verified sale-date daily CSV reconciles all seven days, 26 October–1 November, against
merchandise and Sales Feed. The old webhook mixes sale-date filters with reconciliation-date
grouping and adds adjustments again. Preserve the existing token-gated endpoint and source/date
canonical identity; replace its accepted CSV contract with the exact six verified columns. Reject
legacy reconciliation headers before canonical writes so the old schedule cannot overwrite
corrected facts. Site 96181 is the supported single venue because daily identity has no site key.

Validate the entire report before writing. Store raw bytes first and stage outcomes explicitly.
Publish reported net/tax/gross through CanonicalDailySalesIngest and existing resolved projections;
keep reconciliation rules/overrides unchanged. No schema, dependency, UI, startup repair, source
configuration or deployment change is needed. Historical daily repair remains separately reviewed.

Use the unchanged non-personal seven-day CSV as the real fixture. Verify legacy rejection, malformed
input, money/site/date validation, canonical history/idempotence and resolved totals on PostgreSQL.
The cutover must pair report changes with deployment because the legacy format fails closed.
