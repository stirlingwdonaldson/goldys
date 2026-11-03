# Invoice read correctness: identity, amount basis and evidence

Date: 16 October 2026. Status: approved and implemented on task branch; verification recorded in the implementation report.
Baseline: `codex/data-foundation-repairs` at `0f88ebd5d179d3cbeb74dae41ce4988ac0d14fb7`.
Main remains `54af254`; these repairs have not been pushed or deployed.

## Why this is the next increment

The previous repairs protect invoice storage and stop unsafe number-only reads. The remaining
read paths discard those qualified identities, so safe failure can affect unrelated invoices.
Finish existing invoice functionality before adding stock valuation, forecasts or business exports.

Requirements authority remains SRS §1.1–1.2 and the user's subsequent decisions. Established
BR-001, BR-003–BR-007, BR-009–BR-010 and ACC-001–ACC-003/ACC-008 apply. G08/G15/G19 are
historical gap references, not proof of current behaviour. DR-018/DR-040/DR-041 and REL-001–003
provide related proposed detail contracts; this plan does not adopt their entire feature scope.
User decisions: performance excluding GST, invoice purchases rather than COGS, owner-only detailed
finances/raw evidence, labelled partial subtotals, no inferred export completeness or expiry.

## Current evidence

| Finding | Current implementation | Consequence |
|---|---|---|
| Identity is lost at the read boundary | `canonical/InvoiceMetadataView.java` and `EnrichedInvoiceLine.java` expose numbers but no invoice logical ID. | Applications and graph consumers cannot select the already-qualified stored relationship. |
| One collision can fail the whole list | `InvoiceReportingService.list()` calls `InvoicePdfLinkService.linkedInvoiceNumbers()`, which checks every linked number; supplier metrics call `CanonicalInvoiceQuery.currentSupplierNames()`, which rejects any duplicate. | An unrelated collision, even outside the requested period, can block an otherwise valid screen. This is safe isolation failure, not permission to restore first-match behaviour. |
| Frontend row identity is invoice number | `frontend/app/(app)/invoices/page.tsx` uses it for row ID, selection, navigation and row ordering. The graph also stamps number-based node IDs. | Same-number invoices cannot be selected independently. |
| Supplier graph mixes amount bases | `InvoiceGraphServiceImpl.suppliers()` sums line totals, then falls back to `InvoiceMetadataView.totalAmount` only when the entire supplier has no lines. | Header totals can have a different GST/freight basis. Header-only invoices disappear from the selected measure once another invoice has lines. Neither omission nor basis change is qualified. |
| Missing WET looks like zero | `InvoiceLineMetricsServiceImpl.cogsBySupplier()` initializes WET to zero and skips nulls. | An all-unknown WET group is reported as a known zero. |
| Raw PDF authorization is broader than raw explorer authorization | `InvoiceDocumentService.pdf()` requires `inventory.cost`; `DataExplorerService.rawDetail()` requires `connectors`. | A custom role with cost access but no source access can reach raw PDF bytes. Existing owner-only seeds limit default exposure; custom-role policy is not enforced here. |
| PDF links still lack parent identity | `InvoicePdfLink` stores raw UUID and number; repository uses unordered `findFirstByInvoiceNumber`. | Adding UUID routes alone cannot safely qualify old PDF relationships or choose a document revision. |

These findings come from source and existing test inspection. No new production audit, incident
count, sample access or executable characterization was performed for this planning task. Existing
supplier graph tests cover all-lines and no-lines cases, but omit mixed availability/tax bases.

## Design

### A. Carry invoice identity through existing reads

Expose the existing stable invoice logical UUID as `invoiceId` on invoice list/detail and graph
invoice nodes. Carry the existing `invoiceLogicalId` on enriched line views. Use these IDs for joins,
row keys, selected invoice, detail requests, graph drill state and share links. Carry the existing supplier logical ID for supplier grouping/node identity as well; names remain
display labels. Do not derive a new ID from those display fields or reinterpret upstream matching.

Add explicit UUID endpoints (for example `/api/invoices/by-id/{invoiceId}` and graph
`/api/inventory/graph/invoices/by-id/{invoiceId}/lines`) rather than overloading numeric/UUID-looking
invoice numbers. Keep legacy number endpoints: resolve exactly one current invoice or return the
existing controlled validation error. Never pick the first supplier. Frontend new links use IDs;
legacy links resolve safely and retain an explicit ambiguity state. Live and demo contracts change
together, including collision fixtures.

List availability must not depend on global PDF ambiguity. Initially retain guarded PDF access and
return per-invoice PDF availability/qualification; an unresolved document is not evidence that no
PDF exists. Do not expand ingestion/enrichment to match ambiguous PDFs in this slice. Number-only ingestion
flags must likewise remain explicitly unscoped where ambiguous, rather than being attributed to
every same-number invoice.

Use the existing single-source invoice read exception and semantic/application permission boundary.
Do not add a second general query engine, a generic AI tool or a new domain projector to serve details.

### B. Make supplier purchases a comparable subtotal

Supplier purchase breakdowns and graph supplier amounts use the same current invoice-line ex-tax
basis as `resolved_inventory_day` before explicit overrides. Group through parent UUID, retaining an
explicit unresolved-supplier bucket for unparented lines. Never replace missing lines with gross
header totals. Show header ex-tax amount separately as source evidence where available, with line
subtotal and difference; header amount is not an additional purchase contribution.

Example: one supplier has invoice A with $100 ex-tax lines/$110 inclusive header and invoice B
with a $220 inclusive header but no lines. Show `$100 known invoice purchases — Incomplete`, with
one invoice lacking lines. Do not show $330 or claim a complete $100 total. If B's ex-tax header is
$200, show that separately as header evidence, without silently changing the metric's basis.

Unknown WET stays unknown: all-null → unavailable; mixed → known subtotal with a qualification;
explicit zero → known zero. Preserve decimals through calculations and format money to cents at the
UI boundary. Existing two-cent reconciliation tolerance is inherited implementation behaviour,
not newly approved business policy. Do not infer pack conversions, tax treatment or consumption.

### C. Qualify original documents without guessing revisions

Add an optional invoice logical ID to PDF-link evidence in a new migration (provisionally V95;
recheck version availability immediately before implementation). New links may carry it only where
existing source evidence establishes the relationship. Existing rows remain intact and nullable.
Do not bulk-assign old links by invoice number alone or modify raw records/canonical history.

Provide UUID-qualified link queries and retrieve the selected document by raw document ID after
checking that it belongs to that invoice. Return bytes and filename from the same link record.
If several documents are linked, expose the known documents for explicit selection rather than
inventing latest-is-authoritative precedence. Unqualified historical links remain visibly unresolved
unless reviewed evidence uniquely establishes the parent. A legacy single-document compatibility
path must prove unique attribution, including relevant identity history; otherwise fail explicitly.

PDF retrieval requires both invoice-detail access (`inventory.cost`) and source-record access
(currently `connectors`). This reuses the existing raw access contract, grants no new roles and uses
no hardcoded OWNER bypass. An administrator needs both explicit grants for invoice PDFs. Invoice
summary/detail access must remain usable without raw access. Explain denied PDF access in the UI.

## Boundaries and rollout

A and B can ship as local implementation increments without schema changes; C needs one additive
migration and its own review. They build on the repaired integration branch, not independently on
main. Retain compatibility adapters until consumers and saved links have been exercised.

No historical re-key, replay, link repair, migration execution, push or deployment is included.
Environments that applied old foundation V74–V80 need a separate transition plan. Source expiry,
CTB selected/unexported semantics, revision precedence and Return signs remain unresolved.
