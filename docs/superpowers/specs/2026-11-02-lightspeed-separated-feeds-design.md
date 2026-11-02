# Lightspeed separated financial and receipt evidence

The seven-day source investigation demonstrated payment joins can multiply merchandise measures
without repeating line IDs. Removing those fields reconciled daily amounts to independent Sales
Feed exports. A measure-free line/receipt-number mapping preserved observed coverage, but shared
numbers and linked refunds do not establish a unique parent transaction.

Keep the existing guarded financial writer. Add independently retained scoped mapping and manual
transaction observation batches, with canonical bitemporal provenance and resolved SQL views.
Use additive evidence: unknown source revision ordering does not justify treating omission as
deletion or the latest uploaded snapshot as truth. Conflicts require review.

Receipt-number search returns a qualified group. Transaction/link observations identify ambiguity,
including later imported refunds, but do not assign lines. Withhold transaction-level costing
until an explicit source bridge is verified; preserve per-line recipe estimates.

The real-data schema, tests, API operations and activation limits are documented in
../../operations/lightspeed-separated-feeds.md. No production report, schedule, data or deployment
is changed by implementation. Manual Sales Feed CSV import is the only proven transaction path.
