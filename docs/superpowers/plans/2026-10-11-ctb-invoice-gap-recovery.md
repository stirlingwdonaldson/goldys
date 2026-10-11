# CTB Invoice Gap Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When CTB's invoice export provably omits line items that the supplier PDF carries, create canonical invoice lines from the PDF so purchases/COGS are complete — without ever double counting.

**Architecture:** Extract ex-tax line amounts from PDFs; per invoice compare `Σ CSV lines` and `Σ PDF lines` to the authoritative header total (`canonical_invoice.amount_ex_tax`). Only when the PDF reconciles and the CSV does not, materialise the PDF-only lines as canonical lines anchored to the **PDF** raw record and marked `origin='PDF'`. When the CSV later catches up, supersede the PDF lines (convergence).

**Tech Stack:** Java 25, Spring Boot, JPA/Hibernate (bitemporal `close-and-insert`), Flyway, PostgreSQL 16 (Testcontainers), JUnit 5 + AssertJ + Mockito, Gradle 9.5 wrapper.

**Spec:** `docs/superpowers/specs/2026-10-11-ctb-invoice-gap-recovery-design.md`

## Global Constraints

- Never edit an applied migration; new schema is a new `V<n>__*.sql` (highest is `V42` after PR #120; use `V43`).
- `hibernate.ddl-auto: validate` — every entity field needs a migration.
- Raw ledger and canonical rows are append-only / close-and-insert; never UPDATE or DELETE canonical or raw rows.
- Columns: `timestamp(6) with time zone` for instants, `numeric(14,4)` for money/quantities.
- New columns on populated tables are nullable or have a default (production has data).
- Per repo rule: extend `docs/connectors/matching-and-identity.md` for matching/identity changes; don't invent tolerances silently — the reconciliation tolerance is a named, documented constant.
- Backend checks before finishing: `./gradlew test spotlessCheck` (from `backend/`).
- **Prerequisite:** PR #120 (`fix/ctb-invoice-line-matching`) is merged to `main` first — this plan branches from `main` after it, and uses `InvoiceLineMatcher` and `invoice_ingest_flag.resolved_at` (`V42`).

## Review Focus

Failure modes the design implies that individual tests may not state, most likely first:

1. **Header is inc-tax or absent.** `canonical_invoice.amount_ex_tax` may be null or reflect a different basis; reconciling against the wrong figure would mass-create bogus lines. → Task 3 tests null header; Task 6 refuses to create without one.
2. **A PDF line has no parsed amount** (unsupported layout). Creating it would fabricate a zero/total line. → Task 6 only creates lines with a non-null `amountExTax`.
3. **CSV already complete.** If both reconcile, gap creation would double count. → Task 3/6: `CSV_COMPLETE` ⇒ create nothing.
4. **GST/rounding.** CSV sum and header differ by cents legitimately. → Task 3 tolerance edges; Task 6 uses the configured tolerance.
5. **Convergence.** After CTB fixes the export the same line exists twice. → Task 9 supersedes PDF-origin lines when the CSV reconciles.
6. **Idempotent replay.** Replaying the same PDF must not create a second line. → Task 6 uses a content-stable identity; Task 10 asserts idempotence.

---

### Task 1: `PdfExtractedLine` carries an ex-tax line amount

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/PdfExtractedLine.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/DeterministicInvoicePdfExtractor.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/ProducePackInvoicePdfExtractor.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/BeverageCaseInvoicePdfExtractor.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/LiquorWetInvoicePdfExtractor.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/LlmInvoicePdfExtractor.java` (the forced JSON schema string)
- Test: existing `*InvoicePdfExtractor*Test` classes (update constructors)

**Interfaces:**
- Produces: `PdfExtractedLine(String stockCode, String description, BigDecimal quantity, String uom, BigDecimal unitQuantity, BigDecimal packSize, BigDecimal wetAmount, BigDecimal amountExTax)` — the new component is last; `null` means "unknown amount".

- [ ] **Step 1: Add the component.** Append `BigDecimal amountExTax` to the `PdfExtractedLine` record.

- [ ] **Step 2: Fix every call site.** Append `null` as the 8th argument at the four `new PdfExtractedLine(...)` sites (Deterministic :35, ProducePack :30, BeverageCase :39, LiquorWet :34).

- [ ] **Step 3: Add the field to the LLM JSON schema.** In `LlmInvoicePdfExtractor`, add `"amountExTax"` to the forced schema's line properties (nullable number) so deserialization still maps.

- [ ] **Step 4: Run the extractor tests.**

Run: `./gradlew test --tests "*InvoicePdfExtractor*" --tests "*Extractor*"`
Expected: PASS (behaviour unchanged; amounts are null everywhere).

- [ ] **Step 5: Commit.**
```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb/
git commit -m "refactor(ctb): add nullable amountExTax to PDF-extracted invoice lines"
```

---

### Task 2: Header ex-tax and CSV line-sum queries

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceRepository.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceQuery.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInventoryQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/InvoiceGapQueryIntegrationTest.java` (new, `@SpringBootTest` + `PostgresContainerConfiguration`)

**Interfaces:**
- Produces: `CanonicalInvoiceQuery.amountExTaxForInvoiceNumber(String invoiceNumber): Optional<BigDecimal>`
- Produces: `CanonicalInventoryQuery.currentLineTotalForInvoice(String invoiceNumber): BigDecimal` (sum of current `line_total`, `ZERO` when none)

- [ ] **Step 1: Write the failing integration test.**

```java
@Test
void returnsHeaderExTaxAndCsvLineSumPerInvoice() {
  // record invoice INV-7001 amount_ex_tax 100.00 + two lines 30.00, 40.00 via the ingest facades
  assertThat(invoiceQuery.amountExTaxForInvoiceNumber("INV-7001"))
      .contains(new BigDecimal("100.0000"));
  assertThat(inventoryQuery.currentLineTotalForInvoice("INV-7001"))
      .isEqualByComparingTo("70.0000");
}

@Test
void lineSumIsZeroForAnInvoiceWithNoLines() {
  assertThat(inventoryQuery.currentLineTotalForInvoice("INV-9999")).isEqualByComparingTo("0");
}
```

- [ ] **Step 2: Run it and watch it fail** (methods do not exist).

Run: `./gradlew test --tests "*InvoiceGapQueryIntegrationTest*"`
Expected: FAIL — cannot find symbol.

- [ ] **Step 3: Implement the queries.**
  - `CanonicalInvoiceRepository`: JPQL `select i.amountExTax from CanonicalInvoice i where i.invoiceNumber = :n and i.supersededAt is null` returning `List<BigDecimal>`; the facade takes the first present.
  - `CanonicalInventoryQuery`: sum `lineTotal` of `lineRepository.findCurrentByInvoiceNumber(invoiceNumber)`, `BigDecimal.ZERO` when empty.

- [ ] **Step 4: Run and pass.**

Run: `./gradlew test --tests "*InvoiceGapQueryIntegrationTest*"`
Expected: PASS

- [ ] **Step 5: Commit.**
```bash
git add backend/src/main/java/com/goldys/platform/canonical/ backend/src/test/java/com/goldys/platform/canonical/InvoiceGapQueryIntegrationTest.java
git commit -m "feat(canonical): expose invoice header ex-tax and CSV line-sum for reconciliation"
```

---

### Task 3: `InvoiceExportReconciler` — the classification rules

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/InvoiceExportReconciler.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/InvoiceExportReconcilerTest.java`

**Interfaces:**
- Produces:
```java
public final class InvoiceExportReconciler {
  public enum Outcome { NO_HEADER, CSV_COMPLETE, PDF_COMPLETE_CSV_SHORT, UNRESOLVED }
  public static Outcome classify(
      BigDecimal headerExTax, BigDecimal csvLineSum, BigDecimal pdfLineSum, BigDecimal tolerance);
}
```
- Rule: `header==null` → `NO_HEADER`; `abs(csv-header)<=tolerance` → `CSV_COMPLETE`; else if `pdf!=null && abs(pdf-header)<=tolerance` → `PDF_COMPLETE_CSV_SHORT`; else `UNRESOLVED`.

- [ ] **Step 1: Write the failing tests.**

```java
@Test void noHeader() { assertThat(classify(null, bd("10"), bd("10"), e)).isEqualTo(Outcome.NO_HEADER); }
@Test void csvReconciles() { assertThat(classify(bd("100"), bd("100"), bd("0"), e)).isEqualTo(Outcome.CSV_COMPLETE); }
@Test void pdfReconcilesCsvShort() { assertThat(classify(bd("100"), bd("40"), bd("100"), e)).isEqualTo(Outcome.PDF_COMPLETE_CSV_SHORT); }
@Test void neitherReconciles() { assertThat(classify(bd("100"), bd("40"), bd("90"), e)).isEqualTo(Outcome.UNRESOLVED); }
@Test void withinToleranceIsComplete() { assertThat(classify(bd("100.00"), bd("99.99"), bd("0"), bd("0.02"))).isEqualTo(Outcome.CSV_COMPLETE); }
```
(`bd = new BigDecimal(...)`, `e = new BigDecimal("0.02")`.)

- [ ] **Step 2: Run and watch it fail.** `./gradlew test --tests "*InvoiceExportReconcilerTest*"` → FAIL.

- [ ] **Step 3: Implement `classify`** with `BigDecimal` scale-safe comparison (`subtract(...).abs().compareTo(tolerance) <= 0`).

- [ ] **Step 4: Run and pass.**

- [ ] **Step 5: Commit.**
```bash
git add backend/src/main/java/com/goldys/platform/canonical/InvoiceExportReconciler.java backend/src/test/java/com/goldys/platform/canonical/InvoiceExportReconcilerTest.java
git commit -m "feat(canonical): classify invoice export completeness against the header total"
```

---

### Task 4: `canonical_invoice_line.origin` column (Flyway V43)

**Files:**
- Create: `backend/src/main/resources/db/migration/V43__invoice_line_origin.sql`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLine.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/InvoiceLineInput.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineService.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java` (set `origin="CSV"`)
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalInvoiceLineOriginIntegrationTest.java` (new)

**Interfaces:**
- Produces: `InvoiceLineInput(..., String origin)` — new last component; `"CSV"` or `"PDF"`.
- Produces: `CanonicalInvoiceLine.origin(): String`.

- [ ] **Step 1: Migration.**

```sql
alter table canonical_invoice_line add column origin varchar(16);
comment on column canonical_invoice_line.origin is 'CSV or PDF; null = legacy CSV row';
```

- [ ] **Step 2: Write the failing integration test.**

```java
@Test
void recordsTheLineOrigin() {
  lineIngest.record(inputWithOrigin("INV-7100:1", "PDF"));
  assertThat(jdbc.queryForObject(
      "select origin from canonical_invoice_line where source_record_ref='INV-7100:1'", String.class))
      .isEqualTo("PDF");
}
```

- [ ] **Step 3: Run and watch it fail.**

- [ ] **Step 4: Implement** — add `origin` to the entity (nullable column, non-updatable), to `CanonicalInvoiceLine.create(...)` and `InvoiceLineInput`, thread it through `CanonicalInvoiceLineService.recordAt`, and pass `"CSV"` from `CtInvoiceCsvIngestService`. Update the other `InvoiceLineInput` constructor call sites (tests + `CanonicalInvoiceLineEnrichment` successor creation, which must preserve the source line's origin).

- [ ] **Step 5: Run and pass**, then commit.
```bash
git commit -am "feat(canonical): record invoice line origin (CSV/PDF) via V43"
```

**Note:** `CanonicalInvoiceLineEnrichment.enrichLine` creates a successor from `l` — add `l.origin()` to that `create(...)` call so enrichment preserves origin.

---

### Task 5: Populate `amountExTax` for the Infiniti layout

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/InfinitiGroupInvoicePdfExtractor.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/InfinitiGroupInvoicePdfExtractorTest.java`

**Interfaces:**
- Produces: a `DeterministicPdfExtractor` (`@Component @Order(0)`) that parses the Infiniti table and sets `amountExTax` from the `Total Ex Tax` column.

- [ ] **Step 1: Write the failing test** with the real captured text (truncated):

```java
private static final String TEXT = """
    Product Code  Product Description Unit QTY Sup B/O Each Ex Total Ex Tax Total Inc
    B12BG Brush Grill Medium Prince Castle each/1 1 1 0 $175.00 $175.00 $17.50 $192.50
    Total Ex GST: $175.00
    """;

@Test
void parsesTheLineAndItsExTaxTotal() {
  PdfExtractedInvoice inv = new InfinitiGroupInvoicePdfExtractor().extract(TEXT);
  assertThat(inv.lines()).hasSize(1);
  PdfExtractedLine l = inv.lines().get(0);
  assertThat(l.stockCode()).isEqualTo("B12BG");
  assertThat(l.description()).isEqualTo("Brush Grill Medium Prince Castle");
  assertThat(l.amountExTax()).isEqualByComparingTo("175.00");
}

@Test void returnsEmptyOnAnotherLayout() { assertThat(new InfinitiGroupInvoicePdfExtractor().extract("other").lines()).isEmpty(); }
```

- [ ] **Step 2: Run and watch it fail.**

- [ ] **Step 3: Implement** a `DeterministicPdfExtractor` (do **not** extend `LinearizedColumnPdfExtractor` — this layout is one text row per item). Match the header, then per row capture code, description, and the `Total Ex Tax` money column; return empty when the header is absent.

- [ ] **Step 4: Run and pass**, then commit.
```bash
git commit -am "feat(ctb): parse Infiniti invoice PDF lines with ex-tax amounts"
```

**Out of scope (follow-up):** amount extraction for the other layouts. Each new template satisfies the same `PdfExtractedLine.amountExTax` interface; layouts without reliable amounts stay `null` and are skipped by Task 6 (Reviewed Focus #2).

---

### Task 6: `InvoiceGapRecoveryService` — create gap lines only when reconciled

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoiceGapRecoveryService.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/InvoiceGapRecoveryServiceTest.java` (Mockito)
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/InvoiceGapRecoveryIntegrationTest.java` (`@SpringBootTest`)

**Interfaces:**
- Consumes: `InvoiceExportReconciler.classify`, `CanonicalInvoiceQuery.amountExTaxForInvoiceNumber`, `CanonicalInventoryQuery.currentLineTotalForInvoice`, `CanonicalInvoiceLineIngest.record`, `InvoiceLineMatcher.select`, `InvoiceIngestFlagService.flag`.
- Produces:
```java
public void recover(String invoiceNumber, String pdfFilename, String pdfSha256, UUID pdfRawRecordId,
                    LocalDate invoiceDate, List<PdfExtractedLine> pdfLines, Set<Integer> unmatchedIndexes);
```

- [ ] **Step 1: Write the failing unit tests (Mockito).**

```java
@Test void createsPdfLinesWhenPdfReconcilesAndCsvIsShort() {
  // header 100, csv sum 40, pdf lines sum 100, one unmatched line amount 60
  recovery.recover("INV-7200", "a.pdf", "sha", rawId, date, lines, Set.of(0));
  verify(lineIngest).record(argThat(i -> i.origin().equals("PDF")
      && i.amountOrTotal().isEqualByComparingTo("60")
      && i.sourceRecordRef().equals("INV-7200:pdf:sha:0")
      && i.rawRecordId().equals(rawId)));
}

@Test void doesNothingWhenCsvAlreadyReconciles() { /* csv sum 100 */ verifyNoInteractions(lineIngest); }
@Test void flagsButCreatesNothingWhenNeitherReconciles() { /* flag INVOICE_EXPORT_INCOMPLETE, no record */ }
@Test void skipsPdfLinesWithoutAnAmount() { /* unmatched line amountExTax null -> no record */ }
@Test void createsNothingWhenHeaderIsMissing() { /* header null -> no record, flag */ }
```
(`InvoiceLineInput` exposes `lineTotal`; the `amountOrTotal` in the sketch is `lineTotal`.)

- [ ] **Step 2: Run and watch it fail.**

- [ ] **Step 3: Implement `recover`:**
  1. `header = invoiceQuery.amountExTaxForInvoiceNumber(invoiceNumber)`; if empty → `flags.flag(INVOICE_EXPORT_INCOMPLETE, ...)` and return.
  2. `csvSum = inventoryQuery.currentLineTotalForInvoice(invoiceNumber)`; `pdfSum = Σ amounts (ignore nulls)`; `outcome = classify(header, csvSum, pdfSum, TOLERANCE)`.
  3. `CSV_COMPLETE` → return. `UNRESOLVED` → flag and return.
  4. `PDF_COMPLETE_CSV_SHORT` → for each unmatched index with a non-null amount, `lineIngest.record(new InvoiceLineInput("CTB", invoiceNumber+":pdf:"+pdfSha256+":"+i, invoiceNumber, invoiceDate, ProductNameKey.normalize(desc), code, quantity==null?ONE:quantity, unitCostFrom(amount,quantity), amount, null, uom, unitQuantity, packSize, wetAmount, pdfRawRecordId, "PDF"))`.
  5. `TOLERANCE` is a named `private static final BigDecimal TOLERANCE = new BigDecimal("0.02")` with a comment; flag `INVOICE_EXPORT_INCOMPLETE` on any non-`CSV_COMPLETE` outcome.

- [ ] **Step 4: Integration test** — seed a canonical invoice with header 100 and one CSV line 40; call `recover` with a PDF line amount 60 marked unmatched; assert a `PDF`-origin line exists and the invoice's current line sum is 100.

- [ ] **Step 5: Run and pass**, then commit.
```bash
git commit -am "feat(ctb): create PDF-derived invoice lines when the export provably omits them"
```

---

### Task 7: `INVOICE_EXPORT_INCOMPLETE` flag type

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoiceIngestFlagType.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/InvoiceIngestFlagIntegrationTest.java`

**Interfaces:**
- Produces: `InvoiceIngestFlagType.INVOICE_EXPORT_INCOMPLETE`.

- [ ] **Step 1: Add a failing test** that `flags.flag(INVOICE_EXPORT_INCOMPLETE, "INV-1", "a.pdf", null, "csv short by 60.00")` persists and appears in `listOpenFlags()`.
- [ ] **Step 2: Run and watch it fail** (enum constant missing).
- [ ] **Step 3: Add the enum constant** with a javadoc line: the CSV export omitted lines the PDF carried.
- [ ] **Step 4: Run and pass**, then commit.
```bash
git commit -am "feat(ctb): INVOICE_EXPORT_INCOMPLETE invoice flag"
```

---

### Task 8: Wire recovery into enrichment with PDF provenance

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfEnrichmentService.java`
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/CtbSftpPull.java` (capture and pass the raw id)
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbInvoicePdfReenrichRunner.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/InvoicePdfEnrichmentServiceTest.java`

**Interfaces:**
- Produces: `InvoicePdfEnrichmentService.enrich(byte[] pdf, String pdfFilename, UUID pdfRawRecordId)`.

- [ ] **Step 1: Update the failing test** to the new signature and assert that when the matcher returns `NO_MATCH` for a line the service records its index for recovery, and calls `recovery.recover(...)` with the pdf raw id.
- [ ] **Step 2: Run and watch it fail.**
- [ ] **Step 3: Implement** — add the `pdfRawRecordId` parameter; collect unmatched PDF line indexes; after the loop call `gapRecovery.recover(invoiceNumber, flagFilename, sha256Of(pdf), pdfRawRecordId, parsedInvoiceDate?, parsed.lines(), unmatched)`. In `CtbSftpPull.process`, keep `UUID rawId = ingestion.ingestPush(...)` and pass it; same in the replay runner (`pdf.id()`). The invoice date for gap lines comes from `canonical_invoice.invoice_date` (add to Task 2's header query if needed) — do not use the PDF text.
- [ ] **Step 4: Run and pass**, then commit.
```bash
git commit -am "feat(ctb): run gap recovery as part of PDF enrichment, anchored to the PDF raw record"
```

---

### Task 9: Convergence — supersede PDF lines when the CSV catches up

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineService.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineIngest.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineRepository.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/InvoiceGapConvergenceIntegrationTest.java` (new)

**Interfaces:**
- Produces: `CanonicalInvoiceLineIngest.supersedePdfOriginLines(String invoiceNumber): int`.

- [ ] **Step 1: Write the failing integration test** — create a `PDF`-origin line and a `CSV` line for one invoice; call `supersedePdfOriginLines("INV-7300")`; assert the PDF line is closed (`superseded_at is not null`) and only the CSV line remains current.
- [ ] **Step 2: Run and watch it fail.**
- [ ] **Step 3: Implement** — repository query for current lines with `origin='PDF'` by invoice; supersede each (bitemporal close) and publish the date's re-projection event. Call it from `CtInvoiceCsvIngestService` after canonicalising an invoice **only when** `InvoiceExportReconciler.classify(header, csvSum, pdfSum?, TOLERANCE)` is `CSV_COMPLETE` (pdfSum not needed here → pass the CSV sum and header; treat "CSV reconciles" as the trigger).
- [ ] **Step 4: Run and pass**, then commit.
```bash
git commit -am "feat(ctb): converge on the CSV once it includes previously-missing lines"
```

---

### Task 10: Replay/backfill, docs, and full verification

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbInvoicePdfReenrichRunner.java` (already calls `enrich`; confirm it passes `pdf.id()`)
- Modify: `docs/connectors/matching-and-identity.md`
- Modify: `docs/operations/deployment.md`
- Test: extend `CtbInvoicePdfReenrichRunnerTest`

- [ ] **Step 1: Add an idempotence test** — running recovery twice over the same PDF yields one current PDF line (`recover` re-records the same identity → `sameFact` → no new version).
- [ ] **Step 2: Document** the identity, the `origin` column, the `INVOICE_EXPORT_INCOMPLETE` flag, and the reconciliation tolerance in `matching-and-identity.md`; note the gap-recovery replay in `deployment.md`.
- [ ] **Step 3: Full checks.**

Run: `cd backend && ./gradlew test spotlessCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**
```bash
git commit -am "docs(ctb): document invoice gap recovery; verify idempotent replay"
```

---

## Self-Review

- **Spec coverage:** amount extraction (T1, T5); reconciliation (T2, T3); gap-line creation + identity + provenance + `origin` (T4, T6); `INVOICE_EXPORT_INCOMPLETE` flag (T7); wiring/provenance (T8); convergence (T9); replay/backfill + docs (T10). All spec sections map to a task. Per-layout amount extraction beyond Infiniti is explicitly deferred.
- **Type consistency:** `PdfExtractedLine.amountExTax`, `InvoiceExportReconciler.Outcome`, `InvoiceLineInput.origin`, `CanonicalInvoiceQuery.amountExTaxForInvoiceNumber`, `CanonicalInventoryQuery.currentLineTotalForInvoice`, `InvoiceGapRecoveryService.recover`, `CanonicalInvoiceLineIngest.supersedePdfOriginLines` are used consistently across tasks.
- **Review Focus mapping:** #1 → T3/T6; #2 → T6; #3 → T3/T6; #4 → T3/T6; #5 → T9; #6 → T6/T10.
- **Proportion:** the plan specifies interfaces and tests, not bodies; the largest single code step (T6) names the algorithm because the tests alone do not determine identity/rounding.
