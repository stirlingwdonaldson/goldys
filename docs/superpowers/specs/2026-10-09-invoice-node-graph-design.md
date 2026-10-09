# Invoice Node Graph — Design

**Status:** In review
**Date:** 2026-10-09
**Scope:** A whole-universe, date-range-scoped node graph on the Kitchen screen that traces
**supplier → invoices → line items**, reusing the PR #69 `@xyflow/react` flow canvas
(`frontend/components/flow/`). Design only — no implementation.

---

## 1. Objective

Make supplier → purchase lineage visible and navigable with the same node-graph component used by
the pipeline map (`pipeline/pipeline-graph.ts`) and the provenance graph
(`trust/provenance-graph.ts`), without rendering the tens of thousands of nodes a literal
"every supplier → every invoice → every line" picture would produce.

## 2. Decisions made (this session)

| # | Decision | Choice |
|---|---|---|
| A | Graph shape | **3-column progressive drill**: suppliers → invoices → line items. Column 0 always visible; columns 1–2 materialize only after drilling in. |
| B | Scope | **Date-range scoped** (`from`/`to`), matching the Kitchen screen's existing range. |
| C | Dependency | **`@xyflow/react`** via the existing `ColumnGraph` / `FlowCanvas` / `layoutColumns` / `FlowTone` abstraction. No new library. |
| D | Volume heuristic | **Top-N per column + a single "Other" rollup**: suppliers 8, invoices 8, lines 10. The heuristic lives in a pure frontend builder, not the backend. |
| E | Interaction | **Additive drill**: an optional `drill` field on `StepNodeData` + an optional `onDrill` prop on `FlowCanvas`. Clicking a supplier/invoice reveals the next column in place. Line items are leaf nodes (no drill, no navigation in v1). |
| F | Data source | **3 lazy GET endpoints**, one level per drill. Backend returns the full per-level list (ranked, unfiltered by top-N); the collapse is client-side. |
| G | Supplier identity | **Normalized supplier name** (`ProductNameKey.normalize`), as elsewhere. Lines whose `invoice_number` has no current header bucket to an **"Unknown supplier"** node. |
| H | Authorization | `inventory.cost` READ — the same gate as `InventoryReportingService`. |

## 3. The heuristic (pure builder)

`buildInvoiceGraph(input)` → `ColumnGraph`, mirroring `buildPipelineGraph` / `buildProvenanceGraph`
as a pure, deterministic function. The caller supplies the already-fetched per-level data and the
current drill state; the builder returns the nodes/edges/tone mapping.

### 3.1 Node and edge derivation

**Column 0 — Suppliers (always shown).** Distinct normalized `supplier_name` across invoices in the
range, ranked by total spend = Σ line `lineTotal` (fall back to header `totalAmount` when a supplier
has invoices but no lines).

- Top-8 → individual nodes. `subtitle` = spend (AUD) + "N invoices"; `tone` = `neutral`.
- The remainder → one **"Other N suppliers"** rollup node, `subtitle` = combined spend.
- A **"Unknown supplier"** node (`tone` = `warn`) for lines whose `invoice_number` has no current
  header. Present only when such lines exist. It is a **leaf rollup** (orphaned lines have no header
  to drill into), so it carries no `drill` target.

**Column 1 — Invoices (for the drilled supplier).** That supplier's `canonical_invoice` rows,
ordered `invoice_date` desc.

- Top-8 by `totalAmount`; the rest → one **"Other K invoices"** rollup.
- Node: `title` = invoice number, `subtitle` = date + total, `detail` = `purchaseNumber` or
  `pdfFilename`, `tone` = `neutral`.

**Column 2 — Line items (for the drilled invoice).** `canonical_invoice_line` rows for that
`invoice_number`, deduped by `productNameKey` (identical keys summed before ranking).

- Top-10 by `lineTotal`; the rest → one **"Other M lines"** rollup.
- Node: `title` = `productNameKey` (fall back to `stockCode`), `subtitle` = quantity × unit cost,
  `detail` = `uom` when PDF-enriched, `tone` = `neutral`. (`category` is not yet populated by any
  ingest path, so it is omitted from the node.)

**Edges.** `supplier → invoice` (label = invoice count), `invoice → line` (label = line count).
Edge `tone` = `neutral`; dashed `missing` when a supplier has invoices but zero lines.

### 3.2 Tone mapping

Exploration, not health: everything `neutral` by default. `warn` for "Unknown supplier"; `missing`
for a supplier with no lines and for a fully-empty range. Reuses the existing `FlowTone` vocabulary
(`ok | warn | fail | missing | info | neutral`) with no new tones.

## 4. Interaction (drill)

`flow-canvas.tsx` currently maps node click → `router.push(href)`. Add, without changing existing
consumers:

- `StepNodeData` gains an optional `drill?: string` (a semantic id the consumer understands).
- `FlowCanvas` gains an optional `onDrill?: (id: string) => void`. When set and a clicked node has
  `drill`, call `onDrill(node.data.drill)` and do **not** navigate; otherwise fall back to `href`.
- The invoice-graph view owns drill state (`focusedSupplier`, `focusedInvoice`), refetches the next
  level on drill, and re-renders the `ColumnGraph` with the new column. The drilled node is marked
  `emphasis` (existing field). A "← Back" affordance pops the last column.

Line-item nodes are leaves: no `drill`, no `href` in v1 (raw-record drill-down is already reachable
via the Data Explorer).

## 5. Backend data source

Three lazy read endpoints on `InventoryController` (delivery-only). The data path follows the
existing inventory layering (mirroring `InvoiceLineMetricsQuery`): a **semantic** interface
`InvoiceGraphQuery` (a leaf — DTO records only) is implemented in **reconciliation**, reading the
`CanonicalInvoiceQuery` / `CanonicalInventoryQuery` facades, and a thin **application** service
`InvoiceGraphService` authorizes (`inventory.cost` READ) and delegates to it. Filtering is in-memory
from `findAllCurrent()`, consistent with `CanonicalInventoryQuery` (the dataset is small; no new
repository queries are required).

| Method | Path | Returns |
|---|---|---|
| GET | `/api/inventory/graph/suppliers?from=&to=` | `SupplierGraphNode[]` (ranked by spend) |
| GET | `/api/inventory/graph/suppliers/{name}/invoices?from=&to=` | `InvoiceGraphNode[]` (date desc) |
| GET | `/api/inventory/graph/invoices/{number}/lines` | `LineGraphNode[]` (lineTotal desc) |

`{name}` is the normalized supplier name, URL-encoded. The reconciliation implementation is the sole
place that assembles the supplier→invoice→line join (supplier by normalized name, lines by
`invoice_number`), reusing the same identity rules as `matching-and-identity.md`.

### 5.1 DTO records (in `com.goldys.platform.semantic`)

```java
record SupplierGraphNode(String name, int invoiceCount, BigDecimal totalSpend) {}
record InvoiceGraphNode(
    String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount,
    String purchaseNumber, String pdfFilename) {}
record LineGraphNode(
    String productNameKey, String stockCode, BigDecimal quantity,
    BigDecimal unitCost, BigDecimal lineTotal, String uom) {}
```

## 6. API contracts (JSON)

```jsonc
// GET /api/inventory/graph/suppliers?from=2026-09-01&to=2026-09-30
[
  { "name": "A. Foods",   "invoiceCount": 214, "totalSpend": 81230.40 },
  { "name": "B. Beverages","invoiceCount": 98,  "totalSpend": 44112.75 }
]

// GET /api/inventory/graph/suppliers/A.%20Foods/invoices?from=...&to=...
[
  { "invoiceNumber": "INV-1042", "invoiceDate": "2026-09-28",
    "totalAmount": 1420.15, "purchaseNumber": "PO-88", "pdfFilename": "inv-1042.pdf" }
]

// GET /api/inventory/graph/invoices/INV-1042/lines
[
  { "productNameKey": "chicken breast", "stockCode": "CB-1",
    "quantity": 4, "unitCost": 12.5, "lineTotal": 50.0, "uom": "CTN" }
]
```

## 7. Frontend components

- `frontend/components/inventory/invoice-graph.ts` — pure `buildInvoiceGraph(...)` + the top-N /
  rollup constants (`SUPPLIER_TOP_N = 8`, `INVOICE_TOP_N = 8`, `LINE_TOP_N = 10`).
- `frontend/components/inventory/invoice-graph-view.tsx` — stateful view: holds drill state, fetches
  lazily via the three endpoints, renders `FlowCanvas` + a "← Back" affordance.
- Wire into `frontend/app/(app)/kitchen/page.tsx`, sharing the screen's existing `from`/`to`.
- Type additions: `SupplierGraphNode` / `InvoiceGraphNode` / `LineGraphNode` in
  `frontend/lib/api/types.ts`; `getInvoiceGraphSuppliers` / `…Invoices` / `…Lines` on `Api` and its
  `live.ts` / `demo.ts` implementations.
- `FlowCanvas` + `flow/types.ts` gain the additive `drill` / `onDrill` described in §4.

## 8. Edge cases and error handling

- **Empty range** → single `missing`-toned "No invoices in range" node; not a blank canvas.
- **"Unknown supplier"** → shown only when lines with no header exist; tone `warn`.
- **Rollup of one** → never render a rollup for a single item; promote it to a normal node.
- **Permission denied** → reuse the existing `PermissionDenied` state, as `ProvenanceView` does.
- **Fetch failure** → per-level `orNull`-style fallback (as `pipeline-map.tsx`): one unreadable
  level must not blank the graph; surface a muted "couldn't load" detail.

## 9. Testing

- **Backend** — `InvoiceGraphServiceImplTest` (reconciliation): ranking by spend, date filtering,
  "Unknown" bucketing, `invoiceNumber` line join. `InvoiceGraphServiceTest` (application): permission
  rejection + delegation. A `CanonicalInvoiceQuery` test for the new `currentInvoices()` mapping.
- **Frontend** — `invoice-graph.test.ts` (pure builder, mirroring `pipeline-graph.test.ts` and
  `provenance-graph.test.ts`): top-N cutoff, "Other" rollup arithmetic, empty range, unknown
  supplier, dedupe of identical `productNameKey`s. A `flow-canvas` test that `drill` triggers
  `onDrill` and suppresses navigation.

## 10. Non-goals (explicitly unchanged)

- No `canonical_supplier` / `canonical_product` master entities.
- No fuzzy supplier/product matching or alias overrides.
- No cross-source linkage (purchase line → POS item / recipe) — still an unresolved open question.
- No write-back to CTB; no change to ingestion.
- No change to the pipeline map or provenance graph behavior.
