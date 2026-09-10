# Docs index

Read in this order before writing code against this repo:

1. **[system-context.md](system-context.md)** — architecture invariants, tech
   stack, the three-layer data model (raw / bitemporal canonical / resolved
   views), build-order prerequisites, and per-source ingestion reality. This
   is the **source of truth** for architectural decisions. Nothing else in
   this repo overrides it.
2. **[prd.md](prd.md)** — the product requirements doc: problem statement,
   goals/non-goals, phased requirements (MVP Phase 1, full-build Phase 2)
   with acceptance criteria, and open questions. Scopes and sequences
   `system-context.md`'s architecture into shippable work; does not override
   its architectural decisions.

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
