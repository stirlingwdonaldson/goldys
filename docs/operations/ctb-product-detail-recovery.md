# Bounded CTB product-detail recovery

The 26 November 2026 audit found CTB product/day totals for July 8–24 without
source-native detail. The recovery endpoint restores detail from a selected retained
report; it does not re-add product/day totals or certify all historical coverage.

This is an operator action, never a scheduled task or startup backfill. Both endpoints
require the existing `connectors:WRITE` permission. Normal session authentication and
CSRF protections apply. Production deployment and replay require explicit approval;
production inspection sessions remain read-only. Do not enable the broad
`app.source-product-sale-fact-backfill` runner as a substitute.

## Preview

POST `/api/connectors/ctb/product-detail-recovery/preview` with JSON:

```json
{"rawRecordId":"<retained UUID>","payloadSha256":"<64 lowercase hex characters>"}
```

Use the [audited candidates](../../ops/audits/historical-coverage/ctb-recovery-candidates.json)
as a review checklist, not an automatic apply list. There are 16 complete single-page
candidates with 2,184 rows. Compare each preview's day, fact count and amount with the
checklist and current product/day controls. The two July 18 pages remain excluded:
matching advertised counts and adjacent timestamps do not prove common report membership.

Preview runs in a read-only repeatable-read transaction. It checks source/fetcher,
original-byte checksum, a maximum 2 MB / 1,000 rows, complete envelope, unambiguous
durable trading date and unique source identities. Revenue pages, incomplete reports,
conflicting rows, missing measures and ambiguous legacy dates fail closed. A legacy
record is dated from canonical raw provenance, never its capture timestamp.

The plan binds a digest of every prepared fact field and includes quantity/amount sums.
It explicitly declares the existing `numeric(14,4)` storage contract: round half away
from zero, retaining original precision in the immutable raw bytes. A read-only check
of the 16 candidates found 161 total-food-cost values and 90 unit-price values beyond
four decimal places; the other seven numeric fields were within that scale. All nine
measures on all 2,184 rows were JSON numbers; see the [precision evidence](../../ops/audits/historical-coverage/ctb-candidate-precision-2026-11-26.json). Recovery
normalizes to this existing storage contract before hashing and comparing, so a fresh
preview recognises the persisted values rather than proposing another recovery.

The plan reports one of:

- `READY`: no CTB detail versions exist for that day.
- `ALREADY_PRESENT`: every expected current fact matches, including raw provenance.
- `HELD`: existing current or superseded detail differs; no automatic replacement.

These are recovery states, not source-completeness assessments. A full single-page
response establishes only that retained report's pagination completeness.

## Apply and verify

After reviewing a fresh preview, POST
`/api/connectors/ctb/product-detail-recovery/apply` with JSON:

```json
{
  "record":{"rawRecordId":"<same UUID>","payloadSha256":"<same SHA-256>"},
  "approvedPreviewSha256":"<sha256 returned by preview>"
}
```

Apply acquires the CTB day lock shared with ingestion, recomputes the preview, and
rejects changed state or a held plan. It stores the reviewed plan in the raw ledger,
then writes through the canonical ingest facade in one transaction. Every fact retains
the original source raw ID. A postcondition checks all expected fields/provenance;
a silent skip or partial insert rolls back the entire day and records a failed stage
against the plan. Product totals and resolved projections are not rewritten.

Repeating a fresh `ALREADY_PRESENT` preview/apply inserts zero rows. Reusing a preview
from before a successful apply is stale and rejected. After applying, rerun the
[coverage audit](../../ops/audits/historical-coverage/README.md), confirm the expected
source-detail additions, and confirm product/day totals and Lightspeed facts did not
change. Do not equate recovery of these CTB details with repair of 2025 Lightspeed
merchandise history; unsafe payment-joined exports remain held.

## Agent coverage wording

Merchandise history no longer appends the fixed November 2026 audit warning to every
requested period. The tool and worker distinguish unverified completeness from a known
gap, preserve observed quantities, and prohibit inferring missing source days from a
product's matching dates. This does not introduce a persisted completeness certificate,
remove genuine gap evidence, or rewrite already-saved investigation answers.

## Local verification — 26 November 2026

- `./gradlew --no-configuration-cache spotlessApply test spotlessCheck`: passed;
  2,143 tests, zero failures/errors, five skipped. All eight new recovery integration
  tests passed against PostgreSQL 16, including precision-sensitive retries, stale
  previews, existing-fact preservation, provenance dating, source/checksum rejection,
  maintenance permission denial and rollback when ingestion would silently skip a row.
- Worker: Bun typecheck, 23 tests, and build passed. The five audit-script tests passed;
  recovery links and audit JSON were checked. No frontend code changed.
- Ripwire's contract check found no incompatible callers of `recordAt`; the final
  quality scan had no complexity findings. It still flags boilerplate duplication,
  class/test size and framework-invoked entry points. The existing permission and
  transaction patterns are retained without adding a shared cross-domain abstraction;
  this is not a warning-free static-analysis result.

These are local results, not published CI or deployed acceptance. Production replay,
live agent responses, full source-population validation and recovery of held reports
remain unverified. The original full-suite failure was an assertion expecting the
removed notice; the final run above includes its updated evidence-based assertion.
