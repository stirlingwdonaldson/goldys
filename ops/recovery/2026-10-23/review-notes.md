# Review notes

The CTB adjustment changes optional group handling without relaxing the requirements for code, quantity or amount. Identical product/site/group observations contribute once, and conflicting repeats still reject the batch. Source identity and raw receipts remain intact.

Insert-only Lightspeed recovery deliberately differs from ordinary source corrections: an existing key is skipped even if an old report contains different values. PostgreSQL integration tests cover preview with no writes, idempotent recovery, preservation of a newer amount, wrong-dataset/manifest rejection, and holding ambiguous source keys. Concurrent ingestion can win a key insert race; the uniqueness constraint makes that pass fail visibly, and a retry skips the now-existing key. No overwrite or retry loop conceals the race.

The full Spring application is not a maintenance sandbox. The supplied Compose override is a reviewed set of preview options, not permission to start/restart production services. The isolated parser probe has no writing dependencies and was used for real-sample verification instead.

Ripwire contract checks report unchanged existing signatures. Its common-name `add` caller resolver also matches unrelated collection additions; those arity warnings do not identify Java compile failures. New recovery entry points are exercised by PostgreSQL tests despite the static graph missing several Spring/JUnit/reflection edges.

Ripwire quality-delta did **not** pass: it retained nine major heuristic findings, chiefly constructor/value-mapping clone groups, existing-file churn, and increased test/service length. The new helper reuses the original sale-item-to-input mapping for ordinary ingestion and recovery; similarly shaped mappings for other record types are not interchangeable. The additional preview/recovery and regression cases are deliberate scope, not evidence of a clean metric result. No blanket quality acknowledgment or unrelated refactor was applied to silence these findings.
