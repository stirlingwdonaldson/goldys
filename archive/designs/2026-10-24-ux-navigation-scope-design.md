# Existing UX navigation, scope and help

Date: 24 October 2026. Implements remediation plan 1.4–1.9, excluding rostering.
Uses current frontend/API contracts, permissions and shared UI components.

- Reconciliation: bounded server pages for both daily/product exceptions, with one
  visible pager. Existing status/search matching applies to the current server
  page and says so explicitly; API has no global filter contract. Totals come from
  server metadata, never loaded row counts. Failed/denied reads cannot become an
  empty or all-clear result. No unbounded page-fetch loop. Demo product paging
  must honour the same contract. Existing audit history remains separately paged.
- Sales/Reservations: URL stores allowed evidence metric/date and parent page/size;
  Sales source filter also survives reload. Open pushes history, closing pops an
  entry created here or removes evidence parameters on a direct link. Back/Forward
  replay the selection. Restore focus to the current originating Trace button,
  with the report container as fallback for direct links. Existing metric API
  permissions still apply; URL values cannot request another metric.
- Scope: Sales identifies resolved net trend versus all-history per-source
  observations; source filter and table sorting are local to the displayed page.
  Reservations states summary date, inclusive covers range and browser calendar;
  no trading-calendar or comparator is invented. Invoices states inclusive date
  range and invoice-date basis. Existing qualifications remain visible.
- Recipes: rename the existing collapsed group to Recipe evidence, preserving its
  route and position; no role or primary-navigation redesign.
- Mobile: put a direct My work link beside Home controls, above KPI/chart content.
  Verify at 390 px and enlarged text with existing caveats and long labels.
  Measured clipping requires wrapping focus/status controls, bounded filter and
  loading widths, adaptive reservation-card columns, wrapping page controls and
  card/ranked-list details. Preserve the values and qualifications.
- Help: native disclosures at report/reconciliation entry points explain measure
  differences, missing values, Trace, local override meaning and permitted next
  steps. Provenance load failures gain existing ErrorState/Retry. No password
  recovery route is verified in current auth code/operations docs, so it remains
  explicitly deferred; no invented reset/support promise.

Verification: regression tests for URL bounds/allowlist, paging beyond 200,
page-local filters, failed/denied product reads, history/focus/reload, scope text,
mobile access and help. Run frontend typecheck/lint/unit/build and controlled
Playwright checks, then record only observed results. No live-source or formal
accessibility-conformance claim.
