# Complete CTB recipe observations

## Evidence and access
Three user-supplied HAR captures show direct ingredients, explicit sub-recipe usage,
folder/outlet allocation, signed adjustments and distinct batch yield/portion count.
On 2026-10-23 a read-only authenticated login and four requests successfully returned
JSON for recipe 696, negative ingredients for 651, sub-recipe usage for 463, and the
root folder tree. Credentials were supplied transiently; no credential or session is
stored in this change. Sanitised response fixtures provide repeatable evidence.

## Architecture and compatibility
Extend the existing scheduled CTB adapter. The historical architecture/PRD restriction
of CTB AJAX to discovery no longer describes the deployed connector. This integration
uses the already-established one-way AJAX path; no source write-back is introduced.
Full source responses land byte-faithfully in raw before parsing. Failed captures never
replace complete data. Credentials and payloads are excluded from new error messages.

A bitemporal recipe-definition aggregate stores a header and normalized relational
child stock/usage rows. All children belong to that version: replacing a complete
snapshot removes disappeared lines without deleting history. Folder/outlet data is a
separate complete bitemporal catalog, including empty folders and explicit deallocation.
Each observation references all contributing raw records. Source valid time is the
capture timestamp, not a guessed historical effective date. Recorded time is ingestion
or replay time. Older/repeated captures cannot overwrite newer knowledge. Resolved
JSON documents are disposable projections of these normalized canonical aggregates.

Existing recipe costs, prices and ingredients remain compatible. Complete definitions
supply newer costs/composition where available, while old records remain usable when
not yet refreshed. CTB prices/profitability are separate source observations and never
replace POS figures or re-enable incomparable cross-source GP calculations.

## Composition rules
Direct stocks and explicit sub-recipe usages are separate. Never sum flattened
sub-recipe descendants with their usage contribution. Preserve negative values,
zero, missing fields, broken links, duplicate names and original unit IDs. Batch
portion count, portion size and unit are independent. Derived recursive costing uses
the child's computed per-portion cost scaled by converted usage / original portion
size, requiring matching child yield/unit and a positive portion count. Cycles,
missing children, stale observations and incompatible units are qualified. Compare
against CTB values using disclosed decimal rounding tolerance, without rewriting them.

## Collection and rollout
A bounded, paced recipe collection helper runs within the existing CTB connector lock.
Regular scheduling is configurable; disabled until an explicit rollout configuration.
Folder traversal must finish before catalog replacement. Recipes commit independently
only after all three detail responses succeed. Capture-specific source references let
replay assemble exactly those three responses without mixing runs. Optional empty
methods/equipment/nutrition/dietary tabs remain deferred; unchecked does not mean safe.

PR 205 owns V99 and overlaps CTB client/config/connector edits. This branch uses V100
and additive methods/helpers; migration versions and overlap must be rechecked before
landing. Work stays in the isolated codex/ctb-recipe-integration worktree. No production
stack operation, deployment or push is part of this implementation authorization.
