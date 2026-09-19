# AI Tool Boundary Contract

- Tools are registered by stable enum identifier.
- Every dimension, metric, aggregation, and filter operator is an enum.
- No parameter accepts SQL, a free-text field name, or free-text filter expression.
- `PermissionService` authorizes the staff profile before dispatch.
- Tools read resolved views only.
- Results may reference widget schema version 1; they never emit executable UI code.
- Phase 1 defines this boundary but does not invoke models or implement business tools.
