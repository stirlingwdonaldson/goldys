# Stale invoice extraction warnings

Read-only production inspection on 12 November 2026 confirmed that the following PDFs
were successfully extracted and their canonical lines updated after earlier
`expired_secret_key` failures. Times below are Australia/Melbourne (AEDT).

| Invoice | Successful extraction | Extracted lines |
| --- | --- | --- |
| 325731 | 12 November, 17:40:09 | 3 |
| 29859 | 12 November, 17:40:19 | 3 |
| INV-00107811 | 12 November, 17:40:20 | 1 |

The pipeline recorded errors but never resolved `LLM_EXTRACTION_FAILED` or
`PDF_UNPARSEABLE` after successful enrichment. The replay runner only retired
`PDF_ONLY_LINE` flags. Failures were not cached, so the expired-key message on
these invoices is a stale flag, not evidence that the successful replay failed.

At inspection there were 113 open LLM extraction warnings, 112 mentioning an
expired key. Of those 113, 84 had a later nonempty successful extraction under the
same invoice number. This aggregate is a candidate count, not proof that every
warning can be cleared: the original PDF identity must also match. One newer
unidentified PDF had an invalid-JSON extraction error and must remain visible.

The fix resolves only extraction warnings for a known invoice and its PDF filename
after nonempty extraction and enrichment complete. Failed/empty retries and
unrelated warning types remain open. Resolved flags remain historical records;
an identical later failure can create a new open warning.

## Applying the fix

Publishing, deployment and production replay require explicit approval under
AGENTS.md; none was performed during this investigation. After the fix is reviewed
and deployed, run the existing opt-in invoice PDF replay described in
[deployment.md](../../../docs/operations/deployment.md#invoice-pdf-replay-one-off).
Successful cached extractions are reused. The replay can call the LLM for uncached
PDFs; unresolved errors must remain visible. Disable the one-off replay setting
again after completion. No credential changes or direct flag-table cleanup are
required for the three verified invoices.

Verify that the three invoices' extraction warnings are resolved, their historical
flags remain available through `?status=all`, and their invoice lines are retained.
Do not blanket-resolve every expired-key warning or infer document success from an
invoice number alone.
