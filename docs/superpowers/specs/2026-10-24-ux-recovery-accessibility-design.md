# UX recovery and accessible existing interactions

Date: 24 October 2026. Builds on the locally verified trust batch `d4a608b5`.
Plan: [Priority 1.1–1.3](../plans/2026-10-24-ux-ui-remediation.md).

Use the existing frontend error, table, form and forecast-run contracts. No backend,
source, permission, model, schema or dependency change; rostering remains excluded.

## Chat recovery

Preserve Error objects, including ApiError HTTP metadata, through the existing Ask
hook. HTTP 401/auth-required offers sign-in, HTTP 403/not-permitted remains explicit
denial, and ordinary answer/transport failures use ErrorState with Retry. Free-text
SSE error events have no code in the existing server contract; do not infer denied
access from their wording. Preserve the entered question and retry the original
submitted text in the same conversation even if the user starts another draft.
Existing cancellation/generation checks continue to reject late callbacks.

Retry resubmits a question; it does not assert transport idempotency or that a
partially completed earlier turn was never retained on the server. No backend
conversation protocol change is included.

## Keyboard and data equivalents

- Add persistent visible labels using the shared Label primitive for sign-in,
  signup and Ask inputs, preserving autocomplete and existing accessible names.
- Add a named invoice button in each row that opens the same existing detail
  sheet. Stop row-event propagation so it does not create two history entries.
  Restore focus to the current button for that invoice when the sheet closes; URL-driven table renders can replace the original DOM node.
- Add a native View chart data disclosure to shared time-series/bar widgets. Reuse
  the existing paged TableWidget renderer and number formatting. Preserve full
  axis labels and series names; show absent/null points as Unavailable and real
  zero as zero. This is not a general export or a formal accessibility audit.

## Forecast recovery

Keep the selected-run lifecycle in a dedicated frontend hook. Expose failed run reads and history reads with Retry. A failed poll leaves the
issued run unchanged and labels its displayed status as last known/currently
unconfirmed, with the time of the last successful status check. Retry reads the
same run rather than launching another forecast. Cancel scheduled polling on a
new selection; use a generation guard to reject late responses after selection,
new run or unmount. A read in progress receives visible status feedback.

## Acceptance

Focused tests cover typed/uncoded chat errors, original-question retry, chart zero
and gap equivalence, failed status/history reads and stale response rejection.
Browser checks cover 401/403/503 chat responses, keyboard invoice entry/Escape/focus,
chart disclosure, persistent field labels and retry of the same forecast run.
Run the frontend typecheck/lint/test/build gate; record only observed results.
