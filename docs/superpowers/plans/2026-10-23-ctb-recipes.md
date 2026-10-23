# CTB recipe integration implementation plan

1. Verify live authenticated endpoints and extract sanitized real fixtures from all HARs.
2. Add typed semantic facts and normalized bitemporal complete recipe/catalog aggregates;
   add V100 after checking V99 is reserved by concurrent wastage PR 205.
3. Add validated CTB client/parser and paced bounded collection within the current connector;
   preserve all source responses raw before parsing and isolate recipe failures.
4. Add capture-aware replay preserving observation timestamps and complete-snapshot semantics.
5. Project into resolved definitions/catalog, implement signed nested costing and qualifications,
   and expose permission-gated composition/catalog endpoints through semantic interfaces.
6. Extend the existing recipe ingredients/costing paths compatibly; add a composition panel with
   live/demo API support and update the existing bounded Ask Goldy's tool.
7. Test real examples, snapshot removal, older/repeated replay, partial refresh, hierarchy,
   cycle/missing-unit behavior and permissions. Produce a real-sample coverage report.
8. Run fast targeted tests during editing; full backend test/spotlessCheck and frontend
   typecheck/lint/test/build before completion. Run Ripwire quality-delta and change checks.
9. Record branch/SHA/check results and concurrent overlap. Push/merge/deploy only when approved.
