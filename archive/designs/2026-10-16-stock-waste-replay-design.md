# Stock/count/waste receipt replay protection

Based on verified local `1cbaa71`. This is a correction to existing ingestion, not a new source,
parser, valuation or reporting feature. Existing source-access/matching documentation and the prior
read-only 3-page count / 3-page waste sample establish the source fields; no new production access
is needed. Count details have source line IDs and quantities/units but no date. Header date is the
existing date basis. BR-001/002/004/005/007/009/010 and INT-004/005 apply; detailed proposed history/lineage
contracts are context, not adoption of unrelated proposed capabilities.

Use one inventory receipt repository/table for HEADER, COUNT and WASTE. It records every accepted
receipt, including equal-value observations. Reception order `(raw.fetched_at, raw.id)` is the
existing deterministic replay convention, not proof of the vendor's business revision chronology.
Repeated/older receipts cannot replace current facts. Unknown source revision/delete/completeness
policy remains unresolved; absence does not remove records.

Count receipts additionally identify the canonical stocktake header version used for date context.
The canonical writer resolves the current same-source header, ignoring a stale caller date hint
when that header exists, and records its immutable version ID on newly created count facts.
The same latest detail can therefore be re-evaluated after a header date change, while an older
detail cannot restore its quantity under the new header context. Equal date/values do not create a
count revision solely because header metadata changed. Headerless compatibility callers retain an
unverified date context; their already-seen raw receipt cannot be reused to invent a date correction.
Existing canonical rows keep unknown header lineage; no header is retroactively guessed.

V98 adds the receipt table, a nullable FK to the count's header-version lineage and indexes. Seed
receipts only from the raw references already present in canonical history; do not re-key or repair
business facts. Receipt and canonical writes share transactions and locks; rollback removes both.
Replay processes header and detail payloads individually through existing batch transactions,
using deterministic raw references and bounded byte loading. Missing header dates fail explicitly.
The SQL observation view continues to follow canonical current versions; API/permissions are unchanged.

Regression gates: reproduce old-fact restoration/artificial history first; equal intermediate and
unseen older receipts; deterministic ties; header date change with same/latest/older detail;
lineage FK and retained history; batch rollback/retry; legacy V97 upgrade seeding; repeated full
runner passes; existing parser/service/observation/architecture tests and full backend gate.
No source writes, production migration/replay/repair, push or deployment.
