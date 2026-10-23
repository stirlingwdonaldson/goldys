# Product, coverage and observations implementation

1. Establish retained-source schema/access and sample parser hit rates (no production ingestion).
2. Extend source product observations and canonical product inputs with site/code identity and
   contributor references; record complete fetched-day batches atomically. Preserve legacy facts
   and block ambiguous compatibility projections. Test source/date/name moves and corrected replay.
3. Add requested-grid coverage states to metric provenance/notices; preserve qualified subtotals,
   missing ratios and the distinction between calendar and trading-population completeness.
4. Add a disposable SQL-view count/waste observation projection and
   semantic read interface. The view follows current canonical versions directly, avoiding new events or rebuild machinery. Add authorized paged reads with explicit units/date/partial qualifiers;
   keep current inventory valuation fields unavailable.
5. Add live/demo frontend reads and a qualified observation table. Run focused tests while editing,
   then full backend/frontend/audit gates, Ripwire contract/quality checks and document limitations.

No push, external source mutation, production migration, replay or deployment. Original checkout
and unrelated uncommitted work are preserved. Source raw payloads remain in memory during probes;
only aggregate schema/hit-rate evidence is written to documentation.
