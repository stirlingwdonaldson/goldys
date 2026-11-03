# Product identity, coverage and inventory observations

Approved implementation, based on `1b3500e`. Existing monolith, raw immutability, bitemporal
supersession, table permissions and semantic read boundaries remain unchanged.

Source discovery: read-only retained-ledger access established on 16 October. Three CTB product
pages (379 rows), three count pages (711 rows), three waste pages (3 rows); all nine digests agree.
Product rows contain stock code, site number, opaque group and quantities/revenue, but no sale date.
The selected product pages have canonical date lineage; their raw request references are absent.
This is sample/schema evidence, not proof of global uniqueness, complete business coverage or
source correction precedence. No source login, production ingestion or repair was run.

## Product slice

Preserve CTB source product/day at code+site grain before name aggregation. Keep group-native
observations with quantity/amount/name/site, and every contributing page reference. Keep display
name separate from source identity. Identical observations/replay must not manufacture revisions;
rename invalidates old and new name projections. Existing historical facts are not rewritten.

The existing business projection is a name-based compatibility view. It may reconcile sources
only when each source supplies one unambiguous product for that name/day. Multiple same-source
identities become an explicit unresolved conflict, before override/rule selection; never select the
first code or add duplicate representations. Qualified observations supersede legacy aggregates
only in the compatibility read for the same source/day after a complete fetched-day batch. A superseded alias must not leave a
second current business value. Full cross-source hub/variant equivalence still needs reviewed
mapping evidence; this slice does not promote automatic name-based hub suggestions to identity.

## Coverage slice

Extend metric provenance with requested calendar dates, known values, unresolved stored values,
and absent projection dates. Calendar coverage is not expected trading coverage. Missing input
scope survives period summaries, saved widgets and fixed tools through existing notices. Preserve
known subtotals and ratios-unavailable behaviour; do not derive missing money or statistical bounds.
No age-based expiry or new upcoming-booking TTL is introduced.

## Inventory observation slice

Add a disposable resolved SQL view for current count/waste source facts with stocktake-header context,
with per-record identity, source raw lineage, original units, business observation date and header
partial/locked flags. The view follows canonical supersession and header corrections without introducing a second event/rebuild system. Canonical history is unchanged.
Expose paged records through a semantic port and permission-gated application/API/live+demo path,
plus the existing Kitchen screen. Do not sum heterogeneous units or publish current stock, usable
stock, consumed cost, valuation or a global waste percentage. Source waste cost is a separate
observation, not a proven ex-tax expense. Historical observations never expire solely by age.

Migrations are additive V96 (product grain/lineage), V97 (resolved observations), subject to final
version-conflict check. Old unique name index changes to source-reference identity; canonical rows
are not mass re-keyed. No production migrations/replay/deployment are authorised.

Verification: real-sample parser probe; synthetic edge regressions in disposable PostgreSQL;
same-name codes, site scope, rename, legacy coexistence, all page contributors, corrected replay,
metric missing-state distinctions, projection rebuild/correction idempotency, source permissions,
unknown versus zero counts, separate unit scopes, frontend qualifications and full local gates.
