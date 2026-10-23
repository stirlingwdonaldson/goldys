# Invoice read correctness — implementation record

16 October 2026. Task branch: `codex/invoice-read-correctness`, based on repaired integration
`0f88ebd`. Scope is approved plan 1A/1B/1C. Coverage/product follow-ups are recorded in
[follow-up evidence](2026-10-16-follow-up-evidence.md). Production is unchanged.

## Result

- Invoice list/detail DTOs carry stored invoice/supplier logical UUIDs. Detail, supplier/line
  breakdowns and graph reads join those IDs. New explicit UUID routes coexist with guarded legacy
  number/name routes. One collision no longer blocks unrelated lists or totals.
- Frontend row keys, selection/order, graph drill and shared links use qualified identity. Legacy
  links remain supported without treating UUID-looking or `id:`-prefixed vendor numbers as IDs.
  Repeated product-labelled invoice lines remain separate occurrences in the graph.
- A shared line-only supplier aggregation produces known ex-tax invoice purchases. Header totals
  remain separate source evidence; no gross header fallback fills missing lines. Header-only
  invoice counts and incomplete/unresolved qualifications are visible. A known zero remains zero;
  all-unknown WET remains null, and mixed WET has an explicit known-subtotal qualification.
- Invoice details show line subtotal, separate header difference and unresolved source qualifications.
  Number-only ingestion flags are not assigned to each colliding invoice. Existing period/ratio
  safeguards, approved role grants, source signs and conversion uncertainty are preserved.
- PDF evidence carries an optional parent UUID. Document listing/retrieval require both
  `inventory.cost` and `connectors` grants. Invoice detail remains readable with only its own grant;
  the UI explains unavailable source access. A selected link supplies both its filename and bytes.
  Multiple linked documents require explicit selection; no latest-receipt authority is inferred.
- Existing links are preserved. Read-time legacy compatibility requires a single logical invoice
  across number history and a uniquely matching current filename. Otherwise evidence stays
  unresolved. Availability and retrieval use the same qualification. No raw records or historical
  canonical facts are rewritten, and no new PDF/source matcher is introduced.

## Schema and compatibility

V95 adds nullable `invoice_logical_id` plus an index to `invoice_pdf_link`, without a number-only
backfill. It is exercised only in disposable PostgreSQL; applying it in production requires separate
approval. Old foundation V74–V80 migration histories still require their own reviewed transition.
Historical lines with no stored parent remain in an unresolved-supplier bucket and are not attached
to invoice detail by number. Deploying code alone does not reconstruct those relationships; any
historical parent/link repair needs separate evidence and approval.
New source links can capture the existing unambiguous parent; already-present unqualified links are
not silently amended on replay. A later historical-link repair remains a separate task.

API additions:

- `GET /api/invoices/by-id/{invoiceId}`
- `GET /api/invoices/by-id/{invoiceId}/documents`
- `GET /api/invoices/by-id/{invoiceId}/documents/{documentId}/pdf`
- `GET /api/inventory/graph/suppliers/by-id/{supplierId}/invoices`
- `GET /api/inventory/graph/invoices/by-id/{invoiceId}/lines`

Legacy single-document PDF routes reject ambiguous numbers/multiple documents. New financial DTO
fields are additive; TypeScript permits missing new metadata for compatibility with earlier responses,
while the updated live server emits qualified IDs. Demo APIs implement UUID reads and include
same-number, different-supplier fixtures. No generic AI query tool or write-back is added.

## Regression scope

- Disposable PostgreSQL: stored CSV → parent IDs → authorized invoice detail/graph/breakdown;
  same-number invoices, unrelated ambiguous links, source-grant denial/revocation, wrong-parent
  document IDs, paired filename/bytes, receipt idempotency, V94 upgrade with retained unqualified
  link and empty-database full migrations.
- Unit/application/controller: mixed line/header availability and tax bases, null/zero WET, signed
  lines, same-labelled supplier identities, unparented lines, qualified URLs and malformed UUIDs.
- Frontend: duplicate-number row selection, graph identities/repeated lines, explicit PDF choice,
  source denial/unresolved messages, legacy navigation namespaces and collision demo parity.

## Verification and limits

Frontend final gate passed: typecheck, ESLint, 615 tests across 103 files and production build.
The audit harness's eight Python standard-library tests passed; code-derived schema inventory was
regenerated for V95 without changing retained production evidence. No browser end-to-end session,
new real-source parser probe or Python model-service test was run; source parsers/models were unchanged.

Ripwire contract review found the new application detail method and no incompatible indexed callers;
framework calls still rely on controller/integration tests. The final pre-commit quality delta at
`0f88ebd5d+dirty` was not clean: 170 gating findings (118 duplication, 34 helper-clone, nine short-horizon
churn, seven verbosity, one constructor-parameter and one dead-code). Graph node/known-subtotal and explicit route-loading
helpers removed introduced complexity warnings in the existing graph components. The shared new
purchase summarizer remains above the tool's complexity threshold. The reported dead `toSummary`
is called via `items.stream().map(this::toSummary)` and exercised by list tests; the parameter warning
concerns a compatibility constructor on the extended invoice-line record. No blanket acknowledgements
or broad class/DTO redesign was used to suppress the warnings.

Backend final gate: `spotlessApply test spotlessCheck` passed in 3m 31s: 1584 tests
across 344 suites, zero failures/errors and 2 skipped. PostgreSQL integration tests,
V94→V95 migration and architecture boundaries are included. Skipped cases are not claimed verified.

No production payloads were read, dependencies installed, source ingestion run, databases repaired,
services restarted, changes pushed, or migrations/deployment performed during this implementation.
