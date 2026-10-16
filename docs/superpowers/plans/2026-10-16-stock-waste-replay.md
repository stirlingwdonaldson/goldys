# Stock/count/waste replay implementation

1. Reproduce receipt regression and artificial revisions in isolated PostgreSQL.
2. Add V98 receipt tracking/nullable header-version lineage, using existing immutable raw IDs and
   reception ordering. Keep migration seeding confined to evidenced canonical raw references.
3. Wire all three canonical writers to receipt guards; count date context comes from the current
   canonical header. Preserve unchanged observations, atomic version/receipt writes and unknowns.
4. Make the flag-gated replay stream each payload, carry deterministic ordering and validate missing
   headers before recording count facts. Test repeated runner passes and derived date revisions.
5. Run focused regression/upgrade tests, full backend gate, audit harness and Ripwire review.
   Document any limits and commit locally. No frontend/API changes are planned.
