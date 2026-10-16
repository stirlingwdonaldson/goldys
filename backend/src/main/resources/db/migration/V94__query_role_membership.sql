-- The analytical executor explicitly SET LOCAL ROLE inside its own read-only transaction.
-- Flyway uses the application datasource here; deployments using a separate migration principal
-- must grant this membership to the application principal before enabling this query path.
GRANT goldys_ro TO CURRENT_USER;
