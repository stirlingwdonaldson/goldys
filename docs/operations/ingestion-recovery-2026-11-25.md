# Invoice ingestion recovery, 25 November 2026

## Verified production findings

Read-only audit: 11 November 17:56 through 25 November approximately 18:00,
Australia/Melbourne. PostgreSQL sessions used `default_transaction_read_only=on`.
No production changes or replays were performed during the audit or implementation.

- The CTB export with SHA-256
  `2a6e7cbdec051401ce97b5e087374c4b1198f877a962ae0ed5b61c7f9fb94194`
  is retained, for example raw ID `017cea39-57cb-44a0-b0a9-d7243edec631`.
  Its two invoices contain nine lines. There were 436 failed attempts from
  21 November 05:00 through 25 November 17:45.
- The first invoice is Good Bar Trade, number `Unknown`, date 1 October 1989,
  amount $165.30. Another supplier has legacy unparented lines under `Unknown`.
  The guard correctly refuses to infer their supplier, but the unclassified
  exception aborted processing of the next invoice too.
- Paramount Liquor invoice `5772205`, dated 22 November, $646.78 including tax,
  was consequently absent from canonical invoices. Importing its three lines is
  the concrete recovery target; do not reassign the ambiguous legacy lines.
- Good Bar Trade invoice `INV102334` arrived on 25 November, dated 19 November,
  for $165.30. This does not prove by itself that the older `Unknown` export is
  the same enduring source identity. That source correction needs review.
- All 14 Lightspeed sale-item deliveries parsed and canonicalized. Completed
  trading dates 11–24 November have lines, and resolved line totals equal canonical
  line totals. Receipt numbers are absent from the delivered lines; receipt mapping
  remains qualified. No receipt identity is invented by this repair.

### Corrections to the initial audit summary

CUB `7508761889` has a $6,908.02 ex-tax header and $6,908.02 ex-tax line sum.
Good Bar `INV102334` has a $165.30 ex-tax header and $165.30 ex-tax line sum.
The apparently short including-tax sums were sums of partially populated PDF tax
enrichment fields (two missing values on each invoice), not evidence of missing
purchase lines. Never use an incomplete nullable sum as a complete invoice total.

On 13, 14 and 20 November, daily sales deliberately resolve to CTB under the
configured `custom: highest` rule. The $4,166.2128 aggregate difference from
Lightspeed is a policy/source comparison, not an ingestion failure. This change
does not alter that rule or financial values.

## Code safeguards

- Expected invoice identity ambiguities roll back the affected header, supplier
  and lines together, record `INVOICE_IDENTITY_WITHHELD`, and allow later invoices
  to continue. Revision holds retain their existing checks. Unexpected failures
  still fail the file; they are not mislabeled as safe business holds.
- The observed placeholder `Unknown` is withheld before creating a CSV identity,
  even when there is no legacy collision yet. Real historical dates and other
  invoice identifiers are not rewritten or inferred.
- SFTP raw records retain `sftp:<path>` as the source reference. Automatic polling
  defers identical failed bytes for six hours, using persisted evidence across
  restarts. Changed bytes retry immediately. The existing permission-gated SFTP
  run endpoint and **Retry invoice files** bypass this delay explicitly.
- SFTP runs stay `RUNNING` after raw capture until parsing/canonicalization or PDF
  enrichment finishes. A crash in between is exposed by existing startup recovery
  as an interrupted partial run, rather than leaving a false success.
- Failed files remain in the drop. A success for another file cannot hide a held
  file, and a fresh raw capture alone cannot clear it: successful processing of
  that delivery is required. This is delivery health, not a completeness claim
  about all historical source records.
- PDF extraction/identity failures leave the file available and mark enrichment
  failed, rather than reporting a raw capture as completed enrichment. Known
  document replay uses its retained PDF link filename when available. Matching
  extraction/ambiguity flags clear only after successful processing of the known
  invoice and filename.
- Connector cards show actual per-connector outcomes and every current issue.
  Metadata/coverage qualifications do not claim all subsequent figures are absent.
  The invoice-file action is separate from the revenue pull.

No schema migration, changed source identity rule, guessed tax value or automatic
source-system write-back is introduced. Older raw records without delivery references
cannot participate in the new per-file retry queue until that file is attempted
with this version; their existing audit history remains intact.

## Reviewable recovery procedure

Deployment and production operations require the explicit approval in
[agent guidance](../../AGENTS.md). This document is a procedure, not authorization.

1. Deploy the tested change after approval. Allow the scheduled SFTP poll or use
   **Data health → Cooking the Books → Retry invoice files**. It calls the existing
   `POST /api/connectors/CTB/sftp/run` with normal session/CSRF and `connectors` WRITE.
2. Verify `5772205` exists under Paramount Liquor with three current CSV lines,
   $587.99 ex-tax sum and $646.78 header including tax. Verify the old legacy
   `Unknown` facts are unchanged and the Good Bar occurrence remains visibly held.
   A partial outcome is expected until the source identity is resolved; do not
   clear flags or move the retained file just to make the card green.
3. Review the Good Bar source invoice/CSV with its owner. Do not attach the old
   supplier's lines or alias `Unknown` to `INV102334` on amount equality alone.
   A corrected source export can be imported normally; any retirement of the old
   delivery requires review of that exact file and its evidence.
4. Check current model access before PDF replay. Seven identified PDFs failed on
   12 November with `expired_secret_key`; this historical response is not proof
   the credential is still expired. Never print or copy credentials for this check.
5. For approved bounded replay, the existing opt-in startup runner accepts
   `app.ctb-invoice-replay-raw-ids` as a comma-separated selection (maximum 100,
   duplicates removed), alongside `app.ctb-invoice-reenrich=true` and preferably
   `app.ctb-invoice-replay-concurrency=1`. Keep
   `app.ctb-invoice-gap-recovery=false` for this enrichment-only recovery; creating
   PDF-derived purchase lines is a separate reviewed operation. Validate the exact retained IDs below;
   every selection must be a CTB PDF before any selected enrichment starts.
   Remove the replay enablement after the approved run. Do not enable a full-history
   replay merely to clear a few flags; blank selection retains the older full-pass
   behavior. Selected replay does not run the global flag-retirement pass.

| Invoice | Retained PDF raw ID |
|---|---|
| 1178043 | `e48b883f-01a2-4244-b16b-fa16d4885ab4` |
| 29859 | `58c4209d-65da-4eb5-8151-3bb209e06643` |
| 46414 | `00bd22e2-b598-4361-a11d-154470283dc8` |
| 7508789791 | `5e76e9ec-20b5-48e3-8fc5-864beb37fbe4` |
| INV-00107811 | `4b25b010-e19a-4f4a-bcbf-4975f75a48a3` |
| INV102089 | `20bec0ed-f216-41a0-b8dd-afa4326ca647` |
| INV8368250 | `798d91dc-883e-4677-83c9-2d51f4e50053` |
| 5772205 (after CSV recovery) | `16f4fd44-620f-4a7d-b48a-f8582d623bc1` |
| INV102334 | `c01eef2e-a1d9-420c-bbc2-1526fad51017` |

The last two documents were independently text-extracted from the retained PDF
bytes: each contains its listed invoice number and respective $646.78/$165.30
total, and not the other invoice number. Their identities were not assigned by
timestamp proximity alone. Replay them after the invoice imports.
The older invalid-JSON flag with no invoice number
and `PDF_UNPARSEABLE` under `03` also lack a verified document link; leave them open
until identified. Five amount-basis flags and PDF-only-line flags require their
source evidence; neither new retries nor this deployment certifies them resolved.

## Verification

Final local verification passed: backend `test spotlessCheck` reported 2,111 tests,
zero failures/errors and five skipped. Frontend typecheck, lint, 875 tests and build
passed; the final desktop/mobile browser recovery checks passed (two tests).

The sanitized fixture `backend/src/test/resources/fixtures/ctb/invoice-identity-hold.csv`
retains the observed export schema, row order, quantities, money and placeholder
identity; supplier/person/document/product identifiers are replaced. Regression
coverage asserts both invoices and all nine rows parse, the held invoice leaves
no new header or lines, the following invoice imports, raw bytes survive and repeat
attempts do not duplicate canonical facts. Other tests cover durable retry/health
queries, PDF failure retention, bounded replay and the distinct browser actions.

Production verification must be repeated after the approved deployment. Local
PostgreSQL and browser tests do not establish source completeness or live recovery.

The advisory Ripwire pass is not a clean gate: it reports constructor/query-helper
duplication, class/test size and unresolved call-graph coverage. The added invoice
complexity was separated at the parse/canonicalize stage boundary. Actual Java
compilation, PostgreSQL regressions, the full Gradle suite and the browser tests
are the executable evidence; heuristic graph output is not represented as coverage.
