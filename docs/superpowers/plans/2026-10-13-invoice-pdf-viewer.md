# Invoice PDF Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an owner browse invoices on a dedicated screen and open/download the original supplier PDF from the stored raw-ledger bytes.

**Architecture:** A new idempotent evidence table `invoice_pdf_link` maps a PDF raw record to its invoice number, written at enrichment time and backfilled by a one-off runner that re-derives the invoice number from stored PDF bytes. An owner-only `InvoiceController` (`/api/invoices`) lists/drills invoices and serves the PDF bytes as `application/pdf`; a new frontend Invoices screen renders the list, lines, and a preview/download affordance.

**Tech Stack:** Spring Boot (Java 25) + PostgreSQL 16 (Flyway), JPA; Next.js + Bun + Vitest frontend.

**Spec:** `docs/superpowers/specs/2026-10-13-invoice-pdf-viewer-design.md`

## Global Constraints

- Raw ledger is append-only (`raw_record_append_only` trigger); never UPDATE/DELETE a `raw_record` row.
- `hibernate.ddl-auto: validate` — every schema change is a Flyway migration in `backend/src/main/resources/db/migration`; next version is `V51`.
- `api` must never depend on `..canonical..` or `ingestion.RawLedgerQuery` (enforced by `ArchitectureBoundariesTest`); `application` composes/authorizes and may read both.
- Reads authorize via `PermissionService.require(role, resource, READ)` in the application service; the invoices resource is the existing `inventory.cost` (no new seed).
- New permission resources would seed `ALL × OWNER` only — not needed here.
- Backend wrapper is Gradle 9.5.0 (`./gradlew`); format with `./gradlew spotlessApply`.
- Frontend screens never call `fetch` directly; they go through the `Api` interface (live + demo implementations). Missing values render as missing, never as `0`.
- Money uses `numeric(14,4)`; instants use `timestamp(6) with time zone`.

## Review Focus

The five inputs most likely to bite a user, pinned by tests in the owning tasks:

1. **Invoice with no PDF delivered** (`hasPdf=false`) — the list row renders no PDF buttons, and `GET /api/invoices/{n}/pdf` returns 404 (not 500). → Task 4.
2. **Invoice number needing URL encoding** (e.g. contains `/` or a space) — round-trips the `{invoiceNumber}` path variable via `encodeURIComponent`. → Task 4/6.
3. **Scanned/unparseable PDF during backfill** — counted as unresolved, the run completes, no link written, no exception. → Task 3.
4. **Duplicate link writes** (enrichment and backfill both run) — a single row, no exception (idempotent upsert). → Task 1/2.
5. **Unknown `disposition` query value** — treated as `inline`, never a 500. → Task 4.

---

### Task 1: Link table, entity, repository, and service

**Files:**
- Create: `backend/src/main/resources/db/migration/V51__invoice_pdf_link.sql`
- Create: `backend/src/main/java/com/goldys/platform/canonical/InvoicePdfLink.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/InvoicePdfLinkSource.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/InvoicePdfLinkRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/InvoicePdfLinkService.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/InvoicePdfLinkServiceTest.java`

**Interfaces:**
- Consumes: `canonical` package conventions (package-private entity + repository, public service facade) as in `CanonicalInvoice` / `CanonicalInvoiceRepository` / `CanonicalInvoiceService`.
- Produces: `InvoicePdfLinkService.link(String invoiceNumber, UUID pdfRawRecordId, String pdfFilename, InvoicePdfLinkSource source)`, `Optional<UUID> pdfRawRecordId(String invoiceNumber)`, `Optional<String> pdfFilename(String invoiceNumber)`, `boolean isLinked(UUID pdfRawRecordId)`, `Set<String> linkedInvoiceNumbers()`.

- [ ] **Step 1: Write the failing migration test** (none exists; the migration is exercised by `DatabaseMigrationTest`). Add the migration file first so `hibernate.ddl-auto: validate` and `DatabaseMigrationTest` see the table.

Write `V51__invoice_pdf_link.sql`:
```sql
-- V51 - record which raw record is the PDF for a canonical invoice, so the owner can open the
-- original document. A plain idempotent evidence table (like invoice_ingest_flag), not bitemporal:
-- a provenance pointer must not force a supersession of the invoice fact, and the raw ledger is
-- append-only so the link cannot be stored on the PDF raw record itself.
CREATE TABLE invoice_pdf_link (
    pdf_raw_record_id uuid PRIMARY KEY,
    invoice_number varchar(255) NOT NULL,
    pdf_filename varchar(512),
    link_source varchar(32) NOT NULL,
    linked_at timestamp(6) with time zone NOT NULL
);
CREATE INDEX idx_invoice_pdf_link_invoice ON invoice_pdf_link(invoice_number);
```

- [ ] **Step 2: Verify migration applies**

Run: `./gradlew test --tests '*DatabaseMigrationTest'`
Expected: PASS (all migrations apply to an empty PostgreSQL 16 container).

- [ ] **Step 3: Write the failing test** for the link service.

`InvoicePdfLinkServiceTest` (Mockito over `InvoicePdfLinkRepository`):
```java
// link() persists once; a second link() for the same pdfRawRecordId is a no-op
// pdfRawRecordId("INV-1") returns the id after link; empty before
// isLinked(id) true after link; false for an unknown id
// linkedInvoiceNumbers() returns the distinct invoice numbers stored
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew test --tests 'com.goldys.platform.canonical.InvoicePdfLinkServiceTest'`
Expected: FAIL (class not defined).

- [ ] **Step 5: Implement the entity, source enum, repository, and service**

- `InvoicePdfLinkSource` (public enum): `ENRICHMENT, BACKFILL`.
- `InvoicePdfLink` (package-private `@Entity @Table(name="invoice_pdf_link")`): `@Id UUID pdfRawRecordId`, `String invoiceNumber` (nullable=false), `String pdfFilename`, `@Enumerated(STRING) InvoicePdfLinkSource linkSource` (nullable=false), `Instant linkedAt` (nullable=false). Package-private `create(...)` factory and getters, mirroring `CanonicalInvoice`.
- `InvoicePdfLinkRepository` (package-private `interface ... extends JpaRepository<InvoicePdfLink, UUID>`): `Optional<InvoicePdfLink> findFirstByInvoiceNumber(String invoiceNumber)`, `@Query("select l.invoiceNumber from InvoicePdfLink l") List<String> findAllInvoiceNumbers()`.
- `InvoicePdfLinkService` (public `@Service`): `@Transactional link(...)` returns immediately if `repository.existsById(pdfRawRecordId)` is true, else saves a new row with `linkedAt = now`; `pdfRawRecordId` = `findFirstByInvoiceNumber(...).map(InvoicePdfLink::pdfRawRecordId)`; `pdfFilename` = `findFirstByInvoiceNumber(...).map(InvoicePdfLink::pdfFilename)`; `isLinked` = `repository.existsById(...)`; `linkedInvoiceNumbers` = `new HashSet<>(repository.findAllInvoiceNumbers())`.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests 'com.goldys.platform.canonical.InvoicePdfLinkServiceTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V51__invoice_pdf_link.sql backend/src/main/java/com/goldys/platform/canonical/InvoicePdfLink*.java backend/src/test/java/com/goldys/platform/canonical/InvoicePdfLinkServiceTest.java
git commit -m "feat(invoice): add invoice_pdf_link evidence table and service"
```

---

### Task 2: Enrichment writes the link

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfEnrichmentService.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/InvoicePdfEnrichmentServiceTest.java` (extend existing if present)

**Interfaces:**
- Consumes: `InvoicePdfLinkService.link(...)` from Task 1; `CanonicalInvoiceQuery.isKnownInvoice(String)` (existing).
- Produces: no new public API; enrichment now records a link as a side effect.

- [ ] **Step 1: Write the failing test**

Assert, via Mockito, that `InvoicePdfEnrichmentService.enrich(pdf, filename, rawId)` calls `pdfLinkService.link(invoiceNumber, rawId, filename, ENRICHMENT)` when `canonicalInvoiceQuery.isKnownInvoice(invoiceNumber)` is true; and does **not** call `link` when the invoice is unknown. (The existing tests construct the service with mocked collaborators — add the new `InvoicePdfLinkService` mock to that constructor.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.goldys.platform.connectors.ctb.InvoicePdfEnrichmentServiceTest'`
Expected: FAIL (constructor signature changed / no link call).

- [ ] **Step 3: Implement the link write**

In `InvoicePdfEnrichmentService`:
- Add a `private final InvoicePdfLinkService pdfLinkService;` field and constructor parameter.
- In `enrich(...)`, immediately after `flagFilename` is computed, hoist the known-invoice check and write the link, then reuse the boolean for the later `!isKnownInvoice` early return:

```java
boolean known = invoiceQuery.isKnownInvoice(invoiceNumber);
if (known) {
  pdfLinkService.link(invoiceNumber, pdfRawRecordId, pdfFilename, InvoicePdfLinkSource.ENRICHMENT);
}
```
(Replace the existing `if (!invoiceQuery.isKnownInvoice(invoiceNumber)) { flagUnresolvedInvoice(...); return; }` with `if (!known) { ... }` so the check runs once. This links any PDF that resolves by filename or text to a known invoice — even one whose extraction later fails — which is what the viewer needs.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.goldys.platform.connectors.ctb.InvoicePdfEnrichmentServiceTest'`
Expected: PASS.

- [ ] **Step 5: Verify the re-enrich path compiles** (it constructs the same service)

Run: `./gradlew compileJava`
Expected: SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfEnrichmentService.java backend/src/test/java/com/goldys/platform/connectors/ctb/InvoicePdfEnrichmentServiceTest.java
git commit -m "feat(invoice): record invoice_pdf_link during PDF enrichment"
```

---

### Task 3: Backfill runner

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfLinkBackfillRunner.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/InvoicePdfLinkBackfillRunnerTest.java`

**Interfaces:**
- Consumes: `RawLedgerQuery.rawPayloadsForSource(String, String)` → `List<RawPayload(id, bytes)>`; `DocumentTextExtractor.extractText(byte[], String)`; `InvoicePdfExtractor.extract(String)` → `PdfExtractedInvoice(invoiceNumber, lines, extractionError)`; `CanonicalInvoiceQuery.isKnownInvoice(String)`; `InvoicePdfLinkService.isLinked(UUID)` / `link(...)`.
- Produces: `InvoicePdfLinkBackfillRunner` (a `@Component` `ApplicationRunner`), no public API.

- [ ] **Step 1: Write the failing test**

`InvoicePdfLinkBackfillRunnerTest` (Mockito; construct the runner with mocks, call `run(null)`):
```java
// a resolvable PDF (extractor returns "INV-1", isKnownInvoice true) → link(invoiceNumber, id, null, BACKFILL)
// an already-linked PDF (isLinked true) → skipped, no extractor call, no link
// an unresolved PDF (extractor returns null invoice number, or isKnownInvoice false) → no link, counted unresolved
// run() never throws when extractor throws (wrap per-PDF in try/catch like CtbInvoicePdfReenrichRunner)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.goldys.platform.connectors.ctb.InvoicePdfLinkBackfillRunnerTest'`
Expected: FAIL (class not defined).

- [ ] **Step 3: Implement the runner**

Mirror `CtbInvoicePdfReenrichRunner`:
- `@Component @Order(Ordered.HIGHEST_PRECEDENCE) @ConditionalOnProperty(name="app.ctb-invoice-pdf-link-backfill", havingValue="true")`, `implements ApplicationRunner`.
- Dependencies: `RawLedgerQuery`, `DocumentTextExtractor`, `InvoicePdfExtractor`, `InvoicePdfLinkService`, `CanonicalInvoiceQuery`, and `@Value("${app.ctb-invoice-replay-concurrency:8}") int concurrency`.
- `run(...)`: `List<RawLedgerQuery.RawPayload> pdfs = rawLedger.rawPayloadsForSource("CTB", "application/pdf")`; process with a fixed pool of `concurrency` threads; for each PDF: skip if `pdfLinkService.isLinked(pdf.id())`; else `String text = extractor.extractText(pdf.bytes(), "application/pdf")`, `String number = parser.extract(text).invoiceNumber()`, and if `number != null && invoiceQuery.isKnownInvoice(number)` then `pdfLinkService.link(number, pdf.id(), null, InvoicePdfLinkSource.BACKFILL)` and count linked, else count unresolved. Log a summary (linked / already-linked / unresolved), mirroring the re-enrich runner's logging.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.goldys.platform.connectors.ctb.InvoicePdfLinkBackfillRunnerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfLinkBackfillRunner.java backend/src/test/java/com/goldys/platform/connectors/ctb/InvoicePdfLinkBackfillRunnerTest.java
git commit -m "feat(invoice): add invoice PDF link backfill runner"
```

---

### Task 4: Invoices application services and controller

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/InvoiceMetadataView.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/application/InvoiceReportingService.java`
- Create: `backend/src/main/java/com/goldys/platform/application/InvoiceDocumentService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/InvoiceController.java`
- Test: `backend/src/test/java/com/goldys/platform/application/InvoiceReportingServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/application/InvoiceDocumentServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/InvoiceControllerTest.java`

**Interfaces:**
- Consumes: `CanonicalInvoiceQuery.currentInvoices()` → `List<InvoiceMetadataView(supplierName, invoiceNumber, invoiceDate, totalAmount, purchaseNumber, pdfFilename)>`; `CanonicalInventoryQuery.currentEnrichedLines()` → `List<EnrichedInvoiceLine>` (has `invoiceNumber`, `productNameKey`, `stockCode`, `quantity`, `unitCost`, `lineTotal`, `uom`, `invoiceDate`); `InvoicePdfLinkService.pdfRawRecordId(...)` / `linkedInvoiceNumbers()`; `RawLedgerQuery.payloadBytes(UUID)` → `byte[]`; `PermissionService.require(role, new ResourceKey("inventory.cost"), PermissionAction.READ)`.
- Produces:
  - `InvoiceReportingService.InvoiceSummary(String supplierName, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate, BigDecimal totalAmount, String purchaseNumber, boolean hasPdf)`
  - `InvoiceReportingService.InvoiceDetail(String supplierName, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate, BigDecimal totalAmount, String purchaseNumber, boolean hasPdf, List<LineGraphNode> lines)`
  - `InvoiceDocumentService.PdfDocument(byte[] bytes, String filename)`

- [ ] **Step 1: Write the failing tests**

`InvoiceReportingServiceTest` (Mockito):
```java
// list(): authorizes inventory.cost READ; filters by supplier (exact) and invoiceDate in [from,to]; sorts invoiceDate desc then invoiceNumber; paginates in memory; hasPdf=true only for numbers in linkedInvoiceNumbers()
// detail(): returns header fields + lines (filtered to invoiceNumber, sorted lineTotal desc, mapped to LineGraphNode); throws NoSuchElementException when the invoice number has no current header
```

`InvoiceDocumentServiceTest` (Mockito):
```java
// pdf(): authorizes; resolves link → rawLedger.payloadBytes(id); returns PdfDocument(bytes, filename)
// pdf(): throws NoSuchElementException when pdfRawRecordId is empty or payloadBytes returns null
```

`InvoiceControllerTest` (MockMvc or direct call):
```java
// GET /api/invoices/{n}/pdf returns 200, Content-Type application/pdf, Content-Disposition inline; filename from pdf_filename
// ...?disposition=attachment returns Content-Disposition attachment
// ...?disposition=bogus returns inline (no 500)
// GET /api/invoices/{n}/pdf returns 404 when no link (NoSuchElementException)
// an invoice number with a space or slash round-trips via encodeURIComponent on the client and the {invoiceNumber} path variable
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.goldys.platform.application.InvoiceReportingServiceTest' --tests 'com.goldys.platform.application.InvoiceDocumentServiceTest' --tests 'com.goldys.platform.api.InvoiceControllerTest'`
Expected: FAIL (classes not defined).

- [ ] **Step 3: Implement the three classes**

- Extend `InvoiceMetadataView` with `LocalDate dueDate` (add after `invoiceDate`) and update `CanonicalInvoiceQuery.toView` to pass `i.dueDate()`. (`InvoiceGraphServiceImpl` reads the record via accessors, so the new field is backwards-compatible.)
- `InvoiceReportingService` (public `@Service`): `RESOURCE = new ResourceKey("inventory.cost")`. `list(UserRole role, String supplier, LocalDate from, LocalDate to, int page, int size)` → authorize, filter `currentInvoices()` (supplier exact, `inRange` like `InvoiceGraphServiceImpl`), sort `invoiceDate` desc then `invoiceNumber`, paginate in memory (`DataPage<InvoiceSummary>`), `hasPdf = linkedInvoiceNumbers().contains(invoiceNumber)`; cap `size` at 200. `detail(UserRole role, String invoiceNumber)` → authorize, find header from `currentInvoices()` (else `NoSuchElementException`), build `InvoiceDetail` with `hasPdf` and `lines` mapped from `currentEnrichedLines()` (reuse `LineGraphNode` shape: `productNameKey, stockCode, quantity, unitCost, lineTotal, uom`). Define `InvoiceSummary` and `InvoiceDetail` as public nested records.
- `InvoiceDocumentService` (public `@Service`): same `RESOURCE`. `pdf(UserRole role, String invoiceNumber)` → authorize, `pdfLinkService.pdfRawRecordId(invoiceNumber).orElseThrow(...)`, then `rawLedger.payloadBytes(id)` (null → throw `NoSuchElementException`), filename = `pdfLinkService.pdfFilename(invoiceNumber).orElse(invoiceNumber + ".pdf")`. Return `PdfDocument`.
- `InvoiceController` (`@RestController @RequestMapping("/api/invoices")`): three endpoints delegating to the services via `CurrentUserService.roleOf(user)`:
  - `GET ""` → `DataPage<InvoiceReportingService.InvoiceSummary>` (params `supplier`, `from`, `to`, `page`, `size`, ISO date formatting like `InventoryController`).
  - `GET "/{invoiceNumber}"` → `InvoiceReportingService.InvoiceDetail`.
  - `GET "/{invoiceNumber}/pdf"` → `ResponseEntity<byte[]>` with `Content-Type: application/pdf`, `Content-Disposition` built from `boolean attachment = "attachment".equalsIgnoreCase(disposition)`, filename sanitized (strip `/`, `\`, `"`), `Content-Length` set.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.goldys.platform.application.InvoiceReportingServiceTest' --tests 'com.goldys.platform.application.InvoiceDocumentServiceTest' --tests 'com.goldys.platform.api.InvoiceControllerTest'`
Expected: PASS.

- [ ] **Step 5: Verify architecture boundaries**

Run: `./gradlew test --tests 'com.goldys.platform.architecture.ArchitectureBoundariesTest'`
Expected: PASS (the controller reaches only `application` + `auth`, never `canonical`/`RawLedgerQuery`).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/application/InvoiceReportingService.java backend/src/main/java/com/goldys/platform/application/InvoiceDocumentService.java backend/src/main/java/com/goldys/platform/api/InvoiceController.java backend/src/test/java/com/goldys/platform/application/InvoiceReportingServiceTest.java backend/src/test/java/com/goldys/platform/application/InvoiceDocumentServiceTest.java backend/src/test/java/com/goldys/platform/api/InvoiceControllerTest.java
git commit -m "feat(invoice): add invoices list/detail/PDF endpoints"
```

---

### Task 5: Frontend Api methods

**Files:**
- Modify: `frontend/lib/api/types.ts`
- Modify: `frontend/lib/api/live.ts`
- Modify: `frontend/lib/api/demo.ts`
- Test: `frontend/lib/api/demo.test.ts` (extend if present)

**Interfaces:**
- Consumes: `fetchApi` from `client.ts`; `DataPage<T>` and `LineGraphNode` types already in `types.ts`.
- Produces (on `interface Api`): `listInvoices(filter: InvoiceFilter, page: number, size: number): Promise<DataPage<InvoiceSummary>>` and `getInvoice(invoiceNumber: string): Promise<InvoiceDetail>`.

- [ ] **Step 1: Write the failing test** (demo fixtures)

`demo.test.ts`: `listInvoices` returns a page with at least one `hasPdf=true` and one `hasPdf=false` invoice and an empty-list case; `getInvoice` returns a header plus lines, and throws `NOT_FOUND` for an unknown number.

- [ ] **Step 2: Run test to verify it fails**

Run: `bun run test -- frontend/lib/api/demo.test.ts`
Expected: FAIL (methods missing).

- [ ] **Step 3: Add types**

In `types.ts`, mirror the backend records exactly (nullables stay `| null`):
```ts
export interface InvoiceSummary {
  supplierName: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string | null;
  totalAmount: number | null;
  purchaseNumber: string | null;
  hasPdf: boolean;
}
export interface InvoiceDetail extends InvoiceSummary {
  lines: LineGraphNode[];
}
export interface InvoiceFilter { supplier?: string; from?: string; to?: string; }
```
Add the two methods to `interface Api`.

- [ ] **Step 4: Implement live + demo**

- `live.ts`: `listInvoices` → `fetchApi<DataPage<InvoiceSummary>>("/api/invoices?" + new URLSearchParams({...}))` (include only present filter fields, plus `page`, `size`); `getInvoice` → `fetchApi<InvoiceDetail>(`/api/invoices/${encodeURIComponent(invoiceNumber)}`)`.
- `demo.ts`: realistic fixtures with `await delay(300)`; include a `null` `dueDate`, a `null` `totalAmount`, and a `hasPdf=false` row.

- [ ] **Step 5: Run test to verify it passes**

Run: `bun run test -- frontend/lib/api/demo.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/api/types.ts frontend/lib/api/live.ts frontend/lib/api/demo.ts frontend/lib/api/demo.test.ts
git commit -m "feat(invoice): add listInvoices/getInvoice API methods"
```

---

### Task 6: Invoices screen and nav

**Files:**
- Create: `frontend/app/(app)/invoices/page.tsx`
- Modify: `frontend/lib/nav.ts`
- Test: `frontend/app/(app)/invoices/invoices-page.test.tsx`

**Interfaces:**
- Consumes: `useApiData` from `@/lib/use-api-data`; `DataTable`, `DataTableColumnHeader` from `@/components/data-table`; `currencyColumn`/`numberColumn` from `@/components/data-table/columns`; `useDemoMode` from `@/lib/demo-mode`; `api.listInvoices` / `api.getInvoice`.
- Produces: the `/invoices` route; a new `NavItem` in `NAV_GROUPS` (Operations group, after Kitchen).

- [ ] **Step 1: Write the failing test**

`invoices-page.test.tsx`: renders the invoice list from a mocked `listInvoices`; a row with `hasPdf=false` shows no "View PDF"/"Download" control; selecting a row renders lines from `getInvoice`; in demo mode the PDF affordance is replaced by a muted note (assert no `<iframe>`).

- [ ] **Step 2: Run test to verify it fails**

Run: `bun run test -- frontend/app/(app)/invoices/invoices-page.test.tsx`
Expected: FAIL (file not found).

- [ ] **Step 3: Implement the screen**

`app/(app)/invoices/page.tsx` (`"use client"`), following `kitchen/page.tsx` structure:
- Supplier text filter + optional `from`/`to` date inputs; `useApiData((api) => api.listInvoices(filter, page, PAGE_SIZE), [filter, page])`.
- `DataTable` of `InvoiceSummary` columns: Supplier, Invoice, Date (`invoiceDate`), Due, Total (`currencyColumn`), Purchase, PDF (badge `hasPdf ? "Yes" : "—"`).
- Row selection → a side panel fetching `getInvoice(invoiceNumber)`, listing `lines` (stock code, description, quantity, unit cost, line total) and, when `hasPdf`, a **Preview** (`<iframe src={`/api/invoices/${encodeURIComponent(invoiceNumber)}/pdf`}>`) and **Download** (`<a href={`/api/invoices/${encodeURIComponent(invoiceNumber)}/pdf?disposition=attachment`}>`).
- `const { demo } = useDemoMode()`: when `demo`, render a muted "PDF preview unavailable in demo mode" note instead of the iframe/anchor. Handle loading, error (including `NOT_PERMITTED` → `PermissionDenied`), and empty states like the Kitchen screen.

- [ ] **Step 4: Add the nav item**

In `nav.ts`, in the Operations group after Kitchen:
```ts
{ title: "Invoices", href: "/invoices", icon: ReceiptText, keywords: ["supplier invoices", "pdf", "purchases", "documents"] },
```

- [ ] **Step 5: Run test to verify it passes**

Run: `bun run test -- frontend/app/(app)/invoices/invoices-page.test.tsx`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/app/(app)/invoices/page.tsx frontend/lib/nav.ts frontend/app/(app)/invoices/invoices-page.test.tsx
git commit -m "feat(invoice): add Invoices screen with PDF preview/download"
```

---

### Task 7: Documentation and full gate

**Files:**
- Modify: `docs/connectors/matching-and-identity.md`
- Modify: `docs/operations/deployment.md`

- [ ] **Step 1: Document the link table**

In `matching-and-identity.md`, under the invoice metadata section, add a short paragraph: `invoice_pdf_link` maps `pdf_raw_record_id` (PK) → `invoice_number` (+ optional `pdf_filename`, `link_source` `ENRICHMENT`/`BACKFILL`), written at enrichment and backfilled by `InvoicePdfLinkBackfillRunner`; the viewer resolves invoice → PDF through it; note the supplier-scoping coordination with the identity proposal.

- [ ] **Step 2: Document the backfill flag**

In `deployment.md`, next to the existing "Invoice PDF replay" section, document the one-off: set `APP_CTB_INVOICE_PDF_LINK_BACKFILL=true` to run `InvoicePdfLinkBackfillRunner` on startup (idempotent), then unset it.

- [ ] **Step 3: Run the backend full gate**

Run: `./gradlew test spotlessCheck`
Expected: PASS.

- [ ] **Step 4: Run the frontend full gate**

Run: `bun run typecheck && bun run lint && bun run test && bun run build`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add docs/connectors/matching-and-identity.md docs/operations/deployment.md
git commit -m "docs(invoice): document invoice_pdf_link and the backfill flag"
```
