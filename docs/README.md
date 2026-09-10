# Docs index

Read in this order before writing code against this repo:

1. **[PRODUCT.md](PRODUCT.md)** — non-normative orientation summary for
   AI coding sessions: product identity, in-scope sources, the access model,
   scope boundary, and phase gate, each pointing back to its canonical
   section in `system-context.md` or `prd.md`. Read this first for a fast
   pass; never cite it as the source of an acceptance criterion, non-goal,
   or architectural decision — cite the doc it summarizes instead.
2. **[system-context.md](system-context.md)** — architecture invariants, tech
   stack, the three-layer data model (raw / bitemporal canonical / resolved
   views), build-order prerequisites, and per-source ingestion reality. This
   is the **source of truth** for architectural decisions. Nothing else in
   this repo overrides it.
3. **[prd.md](prd.md)** — the product requirements doc: problem statement,
   goals/non-goals, phased requirements (MVP Phase 1, full-build Phase 2)
   with acceptance criteria, and open questions. Scopes and sequences
   `system-context.md`'s architecture into shippable work; does not override
   its architectural decisions.

4. **[design-system.md](design-system.md)** — frontend design principles,
   screen inventory, visual tokens, and core interaction patterns.
   Non-normative for architecture (never overrides `system-context.md` or
   `prd.md`) but is the reference for frontend/UI work, same way the other
   three docs govern backend/product decisions.

## Open questions blocking real feature work

Both tracked in `prd.md`'s Open Questions section — resolve before building
against the areas they touch:

- **Entity-matching strategy** (keys/tolerances that identify "the same
  event" across sources) — must be decided before the reconciliation UI
  (PRD Requirement 5) is implemented.
- **Field-to-role permission mapping** (which fields each department ×
  seniority combination can see) — must be decided before Requirement 3's
  permission table is populated with real data, and before Requirement 5
  is built against it.

## Where the code stands against these docs

See the root [README.md](../README.md) for the current scaffold state
(what's implemented vs. stubbed) and how it maps to spec requirements.
