# Invoice PDF Enrichment (Phase 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enrich the already-canonicalized invoice lines with PDF-only fields — UOM, pack size, and WET — joined to CSV lines on `(invoice_number, stock_code)`, so unit-level costing and liquor-tax accounting work.

**Architecture:** Add structured columns to `canonical_invoice_line`, start persisting the CSV's `StockCode`, then add a PDF extractor (deterministic parser for a known layout, behind a port so an LLM fallback slots in later) and an enrichment service that supersedes matching lines with the PDF's extra fields. CSV stays authoritative for quantity/unit-cost/line-total.

**Tech Stack:** Java 25, Spring Boot 3.5, JPA (Flyway), JUnit 5 + AssertJ + Mockito, Testcontainers/Postgres, Apache Commons CSV, Apache PDFBox (existing). Gradle (`backend/gradlew`). Google-Java-Format via `spotlessApply`.

**Spec:** `docs/superpowers/specs/2026-10-08-pdf-enrichment-design.md`.

## Global Constraints

- **Bitemporal, supersede-not-edit:** enrichment closes the current `canonical_invoice_line` and inserts a successor carrying the same logical identity (`invoice-line:` + invoiceNumber + ":" + sourceRecordRef). Never mutate a row in place.
- **CSV is authoritative:** `quantity` / `unit_cost` / `line_total` come from the CSV and are never overwritten by the PDF. The PDF only adds `uom` / `unit_quantity` / `pack_size` / `wet_amount`.
- **All new columns are nullable** — most lines won't get PDF fields (only ~20% of PDFs state UOM), so absence is normal, not an error.
- **No new raw storage** — the PDF is already byte-faithful in the raw ledger (Phase 1).
- **Java package layout:** canonical entities/records in `com.goldys.platform.canonical`; the PDF extractor + enrichment in `com.goldys.platform.connectors.ctb`.
- **Tests:** unit tests are plain JUnit; integration tests use `@SpringBootTest` + `@Import(PostgresContainerConfiguration.class)` and need Docker. Run from `backend/`.

## Review Focus

1. **Enrichment must not clobber CSV totals** — a PDF with the same `stock_code` but a different quantity/total must leave `quantity`/`unit_cost`/`line_total` untouched, only adding `uom`/`pack_size`/`wet_amount`. → Task 1 + Task 4.
2. **A PDF line with no CSV match must be ignored, not created** (spec decision D: `pdf-only-line`). → Task 4.
3. **Re-enrichment is idempotent** — re-running the same PDF must not append a new version if nothing changed (the existing `sameFact` short-circuit). → Task 4.
4. **`stock_code` is the join key, not `description`** — two lines can share a description but differ by code; enrichment must match on code first. → Task 4.
5. **Deterministic parser must not crash on non-matching text** — a PDF with no recognized table returns an empty result, never throws. → Task 3.

---

### Task 1: Schema + `stock_code` persistence (migration V29)

**Files:**
- Create: `backend/src/main/resources/db/migration/V29__invoice_line_enrichment.sql`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/InvoiceLineInput.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLine.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineService.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineRepository.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java` (pass `stockCode`)
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoicePdfIngestService.java` (compile-fix only)
- Modify (tests): `CanonicalInvoiceIntegrationTest.java`, `InventoryProjectorIntegrationTest.java`, `CanonicalBrowseQueryIntegrationTest.java`
- Test: extend `CanonicalInvoiceIntegrationTest.java`

**Interfaces:**
- Produces: `InvoiceLineInput(..., String productNameKey, String stockCode, BigDecimal quantity, ..., String category, String uom, BigDecimal unitQuantity, BigDecimal packSize, BigDecimal wetAmount, UUID rawRecordId)` — the enriched field set consumed by Task 4.

- [ ] **Step 1: Write the migration**

```sql
-- PDF-enrichment columns (spec §4). All nullable: only a minority of PDFs carry these.
ALTER TABLE canonical_invoice_line
    ADD COLUMN stock_code varchar(255),
    ADD COLUMN uom varchar(32),
    ADD COLUMN unit_quantity numeric(14,4),
    ADD COLUMN pack_size numeric(14,4),
    ADD COLUMN wet_amount numeric(14,4);
```

- [ ] **Step 2: Extend `InvoiceLineInput`**

```java
public record InvoiceLineInput(
    String sourceSystem,
    String sourceRecordRef,
    String invoiceNumber,
    LocalDate invoiceDate,
    String productNameKey,
    String stockCode,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal lineTotal,
    String category,
    String uom,
    BigDecimal unitQuantity,
    BigDecimal packSize,
    BigDecimal wetAmount,
    UUID rawRecordId) {}
```

- [ ] **Step 3: Add fields to `CanonicalInvoiceLine`**

Add (after `productNameKey`):

```java
  @Column(name = "stock_code", updatable = false)
  private String stockCode;
```

Add (after `category`):

```java
  @Column(name = "uom", updatable = false, length = 32)
  private String uom;

  @Column(name = "unit_quantity", updatable = false, precision = 14, scale = 4)
  private BigDecimal unitQuantity;

  @Column(name = "pack_size", updatable = false, precision = 14, scale = 4)
  private BigDecimal packSize;

  @Column(name = "wet_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal wetAmount;
```

Thread all five through the private constructor, `create(...)`, `sameFact(...)` (compare each with `Objects.equals`), and add accessors `stockCode()`, `uom()`, `unitQuantity()`, `packSize()`, `wetAmount()`.

- [ ] **Step 4: Add a join-key query to the repository**

```java
  @Query(
      "select l from CanonicalInvoiceLine l where l.invoiceNumber = :invoiceNumber "
          + "and l.stockCode = :stockCode and l.supersededAt is null")
  Optional<CanonicalInvoiceLine> findCurrentByInvoiceNumberAndStockCode(
      String invoiceNumber, String stockCode);
```

- [ ] **Step 5: Pass the fields through `CanonicalInvoiceLineService.recordAt`**

In the `CanonicalInvoiceLine.create(...)` call, insert `input.stockCode()` after `input.productNameKey()`, and `input.uom()`, `input.unitQuantity()`, `input.packSize()`, `input.wetAmount()` after `input.category()`.

- [ ] **Step 6: Persist `stockCode` in the CSV service**

In `CtInvoiceCsvIngestService`, insert `line.stockCode()` as the new `stockCode` argument (after `ProductNameKey.normalize(line.description())`), and `null` for `uom`, `unitQuantity`, `packSize`, `wetAmount` (after `category`).

- [ ] **Step 7: Compile-fix `CtInvoicePdfIngestService` + the three tests**

Add `null` for the five new fields at the same positions: `CtInvoicePdfIngestService` (after `productNameKey` and after `category`), `CanonicalInvoiceIntegrationTest`, `InventoryProjectorIntegrationTest`, `CanonicalBrowseQueryIntegrationTest`.

- [ ] **Step 8: Add an integration assertion for the new columns**

In `CanonicalInvoiceIntegrationTest`, add a test that records a line with `stockCode`/`uom` and reads them back:

```java
  @Test
  void persistsStockCodeAndUom() {
    InvoiceLineInput line = new InvoiceLineInput(
        "CTB", "INV-3001:1", "INV-3001", LocalDate.of(2026, 9, 20),
        "beef rump cap", "BEEF025", new BigDecimal("3.25"), new BigDecimal("31.50"),
        new BigDecimal("102.38"), null, "KG", null, null, null, rawRecord());
    lineService.record(line);

    assertThat(jdbc.queryForObject(
        "select stock_code || '|' || uom from canonical_invoice_line where invoice_number = 'INV-3001'",
        String.class)).isEqualTo("BEEF025|KG");
  }
```

- [ ] **Step 9: Run the tests**

Run: `./gradlew test --tests com.goldys.platform.canonical.CanonicalInvoiceIntegrationTest --tests com.goldys.platform.reconciliation.InventoryProjectorIntegrationTest --tests com.goldys.platform.canonical.CanonicalBrowseQueryIntegrationTest`
Expected: PASS (Docker required).

- [ ] **Step 10: Full suite, then commit**

Run: `./gradlew test` then `./gradlew spotlessApply`
Expected: PASS.

```bash
git add backend/src/main/resources/db/migration/V29__invoice_line_enrichment.sql \
        backend/src/main/java/com/goldys/platform/canonical \
        backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java \
        backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoicePdfIngestService.java \
        backend/src/test/java/com/goldys/platform/canonical \
        backend/src/test/java/com/goldys/platform/reconciliation/InventoryProjectorIntegrationTest.java
git commit -m "feat(invoice): add enrichment columns + persist StockCode (V29)"
```

---

### Task 2: PDF line contract + extractor port

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/PdfExtractedLine.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/PdfExtractedInvoice.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfExtractor.java`

**Interfaces:**
- Produces: `InvoicePdfExtractor.extract(String text) -> PdfExtractedInvoice`; `PdfExtractedInvoice(String invoiceNumber, List<PdfExtractedLine> lines)`; `PdfExtractedLine(String stockCode, String description, BigDecimal quantity, String uom, BigDecimal unitQuantity, BigDecimal packSize, BigDecimal wetAmount)`. Consumed by Tasks 3 and 4.

- [ ] **Step 1: Write the records + port (no behaviour)**

```java
package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/** A line item parsed from an invoice PDF's text (enrichment only — never overrides CSV totals). */
public record PdfExtractedLine(
    String stockCode,
    String description,
    BigDecimal quantity,
    String uom,
    BigDecimal unitQuantity,
    BigDecimal packSize,
    BigDecimal wetAmount) {}
```

```java
package com.goldys.platform.connectors.ctb;

import java.util.List;

/** An invoice's PDF text reduced to its enrichment-relevant lines. */
public record PdfExtractedInvoice(String invoiceNumber, List<PdfExtractedLine> lines) {}
```

```java
package com.goldys.platform.connectors.ctb;

/** Port every invoice-PDF parser implements; deterministic now, LLM fallback later (spec §6). */
public interface InvoicePdfExtractor {
  PdfExtractedInvoice extract(String text);
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileJava`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb/PdfExtractedLine.java \
        backend/src/main/java/com/goldys/platform/connectors/ctb/PdfExtractedInvoice.java \
        backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfExtractor.java
git commit -m "feat(invoice): PDF enrichment contract + extractor port"
```

---

### Task 3: Deterministic PDF text parser

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/DeterministicInvoicePdfExtractor.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/DeterministicInvoicePdfExtractorTest.java`

**Interfaces:**
- Consumes: `InvoicePdfExtractor` (Task 2), `PdfExtractedInvoice` / `PdfExtractedLine` (Task 2).
- Produces: `DeterministicInvoicePdfExtractor` — a `@Component` implementing `InvoicePdfExtractor` for the "header-anchored table" layout (see the class comment). Used by Task 5.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeterministicInvoicePdfExtractorTest {

  private final DeterministicInvoicePdfExtractor extractor = new DeterministicInvoicePdfExtractor();

  private static final String TEXT =
      "Tax Invoice\n"
          + "Invoice Number\n"
          + "F58991755\n"
          + "QTY\nCODE\nDESCRIPTION\nUNIT\nUNIT PRICE\n"
          + "3.25\nBEEF025\nBEEF RUMP CAP\nKG\n31.50\n"
          + "1\nHAMB008\nBEEF BURGER 150GM\nEACH\n5.00\n"
          + "Total\n117.00\n";

  @Test
  void extractsTheLineTable() {
    PdfExtractedInvoice out = extractor.extract(TEXT);

    assertThat(out.invoiceNumber()).isEqualTo("F58991755");
    assertThat(out.lines()).hasSize(2);
    assertThat(out.lines().get(0).stockCode()).isEqualTo("BEEF025");
    assertThat(out.lines().get(0).description()).isEqualTo("BEEF RUMP CAP");
    assertThat(out.lines().get(0).quantity()).isEqualByComparingTo(new BigDecimal("3.25"));
    assertThat(out.lines().get(0).uom()).isEqualTo("KG");
  }

  @Test
  void returnsEmptyForUnrecognizedText() {
    assertThat(extractor.extract("No table here\nJust words\n").lines()).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goldys.platform.connectors.ctb.DeterministicInvoicePdfExtractorTest`
Expected: compilation FAIL — `DeterministicInvoicePdfExtractor` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser for the "header-anchored table" invoice layout: a block of column headers
 * (QTY / CODE / DESCRIPTION / UNIT / UNIT PRICE, one per extracted line) followed by one 5-line
 * block per line item. Suppliers that linearize their table this way include Tim & Terry Oyster.
 * Unknown layouts return an empty invoice so the caller can fall back to the LLM (spec §6). This is
 * the FIRST template; more get added as more layouts are pinned.
 */
@Component
public class DeterministicInvoicePdfExtractor implements InvoicePdfExtractor {

  private static final Pattern INVOICE_NUMBER =
      Pattern.compile("Invoice Number\\s+([A-Za-z0-9]+)");

  private static final Pattern HEADER =
      Pattern.compile("QTY\\s+CODE\\s+DESCRIPTION\\s+UNIT\\s+UNIT PRICE");

  @Override
  public PdfExtractedInvoice extract(String text) {
    String invoiceNumber = match(text, INVOICE_NUMBER);

    Matcher header = HEADER.matcher(text);
    if (!header.find()) {
      return new PdfExtractedInvoice(invoiceNumber, List.of());
    }

    String[] rows = text.substring(header.end()).trim().split("\\R");
    List<PdfExtractedLine> lines = new ArrayList<>();
    for (int i = 0; i + 4 < rows.length; i += 5) {
      BigDecimal quantity = number(rows[i]);
      BigDecimal unitPrice = number(rows[i + 4]);
      if (quantity == null || unitPrice == null) {
        break; // a non-numeric row ends the table
      }
      lines.add(new PdfExtractedLine(
          rows[i + 1].trim(), rows[i + 2].trim(), quantity, rows[i + 3].trim(), null, null, null));
    }
    return new PdfExtractedInvoice(invoiceNumber, lines);
  }

  private static String match(String text, Pattern p) {
    Matcher m = p.matcher(text);
    return m.find() ? m.group(1) : null;
  }

  private static BigDecimal number(String s) {
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.goldys.platform.connectors.ctb.DeterministicInvoicePdfExtractorTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb/DeterministicInvoicePdfExtractor.java \
        backend/src/test/java/com/goldys/platform/connectors/ctb/DeterministicInvoicePdfExtractorTest.java
git commit -m "feat(invoice): deterministic PDF extractor (first layout)"
```

---

### Task 4: Enrichment service (join + supersede)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineEnrichment.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalInvoiceLineEnrichmentIntegrationTest.java`

**Interfaces:**
- Consumes: `CanonicalInvoiceLineService.record(InvoiceLineInput)` (existing), `CanonicalInvoiceLineRepository.findCurrentByInvoiceNumberAndStockCode` (Task 1).
- Produces: `CanonicalInvoiceLineEnrichment.enrich(String invoiceNumber, PdfExtractedLine line)` — finds the current line by `(invoiceNumber, stockCode)` and supersedes it with the PDF's `uom`/`unitQuantity`/`packSize`/`wetAmount`, keeping the CSV's quantity/unitCost/lineTotal. No-op when no line matches.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.connectors.ctb.PdfExtractedLine;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalInvoiceLineEnrichmentIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalInvoiceLineService lineService;
  @Autowired CanonicalInvoiceLineEnrichment enrichment;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice_line");
  }

  @Test
  void enrichesUomWithoutClobberingCsvTotals() {
    lineService.record(new InvoiceLineInput(
        "CTB", "INV-9001:1", "INV-9001", LocalDate.of(2026, 9, 20),
        "beef rump cap", "BEEF025", new BigDecimal("3.25"), new BigDecimal("31.50"),
        new BigDecimal("102.38"), null, null, null, null, null, rawRecord()));

    enrichment.enrich("INV-9001", new PdfExtractedLine(
        "BEEF025", "BEEF RUMP CAP", new BigDecimal("3.25"), "KG", null, null, null));

    assertThat(jdbc.queryForObject(
        "select quantity || '|' || line_total || '|' || uom from canonical_invoice_line where invoice_number = 'INV-9001'",
        String.class)).isEqualTo("3.2500|102.3800|KG");
  }

  @Test
  void ignoresPdfLinesWithNoCsvMatch() {
    lineService.record(new InvoiceLineInput(
        "CTB", "INV-9002:1", "INV-9002", LocalDate.of(2026, 9, 20),
        "beef", "BEEF025", new BigDecimal("3.25"), new BigDecimal("31.50"),
        new BigDecimal("102.38"), null, null, null, null, null, rawRecord()));

    enrichment.enrich("INV-9002", new PdfExtractedLine(
        "NOPE", "no match", null, "KG", null, null, null));

    assertThat(jdbc.queryForObject(
        "select uom from canonical_invoice_line where invoice_number = 'INV-9002'", String.class))
        .isNull();
  }

  @Test
  void reEnrichingWithSameFieldsDoesNotAppendAVersion() {
    lineService.record(new InvoiceLineInput(
        "CTB", "INV-9003:1", "INV-9003", LocalDate.of(2026, 9, 20),
        "beef", "BEEF025", new BigDecimal("3.25"), new BigDecimal("31.50"),
        new BigDecimal("102.38"), null, null, null, null, null, rawRecord()));

    enrichment.enrich("INV-9003", new PdfExtractedLine("BEEF025", "x", null, "KG", null, null, null));
    enrichment.enrich("INV-9003", new PdfExtractedLine("BEEF025", "x", null, "KG", null, null, null));

    assertThat(jdbc.queryForObject(
        "select count(*) from canonical_invoice_line where invoice_number = 'INV-9003'", Integer.class))
        .isEqualTo(2); // original superseded + one current (no third row)
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)", runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'CTB', 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId, runId, new byte[] {1}, "0".repeat(64), 1);
    return recordId;
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goldys.platform.canonical.CanonicalInvoiceLineEnrichmentIntegrationTest`
Expected: compilation FAIL — `CanonicalInvoiceLineEnrichment` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.goldys.platform.canonical;

import com.goldys.platform.connectors.ctb.PdfExtractedLine;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies PDF enrichment to a canonical line: finds the current line by (invoice_number, stock_code)
 * and supersedes it with the PDF's uom/unit_quantity/pack_size/wet_amount, keeping the CSV's
 * quantity/unit_cost/line_total (CSV is authoritative). A no-op when no line matches.
 */
@Service
public class CanonicalInvoiceLineEnrichment {
  private final CanonicalInvoiceLineRepository repository;
  private final CanonicalInvoiceLineService service;

  public CanonicalInvoiceLineEnrichment(
      CanonicalInvoiceLineRepository repository, CanonicalInvoiceLineService service) {
    this.repository = repository;
    this.service = service;
  }

  @Transactional
  public void enrich(String invoiceNumber, PdfExtractedLine line) {
    Optional<CanonicalInvoiceLine> current =
        repository.findCurrentByInvoiceNumberAndStockCode(invoiceNumber, line.stockCode());
    if (current.isEmpty()) {
      return;
    }
    CanonicalInvoiceLine l = current.get();
    service.record(
        new InvoiceLineInput(
            l.sourceSystem(),
            l.sourceRecordRef(),
            l.invoiceNumber(),
            l.invoiceDate(),
            l.productNameKey(),
            l.stockCode(),
            l.quantity(),
            l.unitCost(),
            l.lineTotal(),
            l.category(),
            line.uom(),
            line.unitQuantity(),
            line.packSize(),
            line.wetAmount(),
            l.rawRecordId()));
  }
}
```

Note: this requires `CanonicalInvoiceLine` to expose `sourceSystem()`, `sourceRecordRef()`, `invoiceNumber()`, `invoiceDate()`, `productNameKey()`, `stockCode()`, `quantity()`, `unitCost()`, `lineTotal()`, `category()`, `rawRecordId()` accessors — add any missing ones in `CanonicalInvoiceLine` (all are package-private; the enrichment lives in the same package).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.goldys.platform.canonical.CanonicalInvoiceLineEnrichmentIntegrationTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineEnrichment.java \
        backend/src/test/java/com/goldys/platform/canonical/CanonicalInvoiceLineEnrichmentIntegrationTest.java
git commit -m "feat(invoice): enrich canonical lines from PDF (join + supersede)"
```

---

### Task 5: Wire PDF enrichment into the SFTP pull

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfEnrichmentService.java`
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/CtbSftpPull.java`
- Modify: `backend/src/test/java/com/goldys/platform/ingestion/CtbSftpPullTest.java`

**Interfaces:**
- Consumes: `InvoicePdfExtractor` (Task 3), `DocumentTextExtractor` (existing), `CanonicalInvoiceLineEnrichment` (Task 4).
- Produces: `InvoicePdfEnrichmentService.enrich(byte[] pdf)` — extracts text, parses, and enriches each line using the parsed invoice number. Called by `CtbSftpPull` for PDF files.

- [ ] **Step 1: Write `InvoicePdfEnrichmentService`**

```java
package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import org.springframework.stereotype.Service;

/**
 * PDF enrichment pipeline: extract text, parse lines, and enrich the matching canonical lines.
 * The invoice number comes from the PDF's own text (the parser's known layout reads it reliably);
 * the CSV-filename mapping is a later hardening (spec §7, see Deferred).
 */
@Service
public class InvoicePdfEnrichmentService {
  private final DocumentTextExtractor extractor;
  private final InvoicePdfExtractor parser;
  private final CanonicalInvoiceLineEnrichment enrichment;

  public InvoicePdfEnrichmentService(
      DocumentTextExtractor extractor,
      InvoicePdfExtractor parser,
      CanonicalInvoiceLineEnrichment enrichment) {
    this.extractor = extractor;
    this.parser = parser;
    this.enrichment = enrichment;
  }

  public void enrich(byte[] pdf) {
    String text = extractor.extractText(pdf, "application/pdf");
    PdfExtractedInvoice parsed = parser.extract(text);
    for (PdfExtractedLine line : parsed.lines()) {
      enrichment.enrich(parsed.invoiceNumber(), line);
    }
  }
}
```

- [ ] **Step 2: Update `CtbSftpPull` to enrich PDFs**

Replace the PDF branch of `pull()` so PDFs are enriched (in addition to being stored raw):

```java
        if (file.filename().toLowerCase().endsWith(".csv")) {
          csvIngest.ingest(bytes);
        } else {
          ingestion.ingestPush(
              "CTB", "ctb-invoice-pdf", FetchMethod.FILE_EXPORT, "application/pdf", bytes, null, "ctb-sftp");
          pdfEnrichment.enrich(bytes);
        }
```

(Add `private final InvoicePdfEnrichmentService pdfEnrichment;` to the constructor, inject it, and add the import.)

- [ ] **Step 3: Update the test**

Add a mock `InvoicePdfEnrichmentService` to `CtbSftpPullTest`, pass it to the constructor, and assert `verify(pdfEnrichment).enrich(any(byte[].class))` in `canonicalizesCsvsAndStoresPdfsRaw` (rename to reflect it now also enriches).

- [ ] **Step 4: Run the tests**

Run: `./gradlew test --tests com.goldys.platform.ingestion.CtbSftpPullTest`
Expected: PASS.

- [ ] **Step 5: Full suite, then commit**

Run: `./gradlew test` then `./gradlew spotlessApply`
Expected: PASS.

```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfEnrichmentService.java \
        backend/src/main/java/com/goldys/platform/ingestion/CtbSftpPull.java \
        backend/src/test/java/com/goldys/platform/ingestion/CtbSftpPullTest.java
git commit -m "feat(ingestion): wire PDF enrichment into the SFTP pull"
```

---

## Deferred (not in this plan)

> **These were implemented as Phase 2.5 below (Tasks 6–12).** Kept here for history.

Per spec §9 and the measured data, these are explicit follow-ups:

- **LLM fallback** (`InvoicePdfExtractor` impl using Spring AI, schema-prompted) — requires the ChatModel to be configured (`SPRING_AI_MODEL_CHAT=openai` + key), which it currently isn't in prod. The port in Task 2 is the seam.
- **More deterministic templates** — the Task 3 parser handles one layout; each additional supplier layout is a new template.
- **OCR** for the 2 scanned PDFs.
- **Flag persistence** (`scanned-pdf`, `pdf-only-line`, `pdf-csv-mismatch`, `pdf-unparseable`) — spec §8; the enrichment currently no-ops on no-match.
- **The `PDF`-filename → invoice-number mapping** (spec §7's preferred join key). Task 5 uses the PDF's own text invoice number, which the deterministic parser reads reliably; the filename→invoice map is the hardening for layouts where the text invoice number is absent or unreliable (e.g. the future LLM path).

---

# Phase 2.5 — LLM fallback, OCR, flags, mapping, reporting (Tasks 6–12)

> These tasks replace the "Deferred" section above. Executed inline with TDD on
> `feature/pdf-enrichment`. Spec authority: `2026-10-08-pdf-enrichment-design.md` §6–§9.

### Task 6: LLM fallback + hybrid extractor

- Create `LlmInvoicePdfExtractor implements InvoicePdfExtractor` (Spring AI; inject
  `ObjectProvider<ChatModel>`; schema-prompt to return the `PdfExtractedInvoice`/`PdfExtractedLine`
  JSON contract; parse with Jackson).
- Create `HybridInvoicePdfExtractor implements InvoicePdfExtractor` — deterministic first, LLM
  fallback when the deterministic result is empty. Make it the single `InvoicePdfExtractor` bean.
- Eval fixtures: a few PDF texts + expected lines to catch hallucination.

### Task 7: More deterministic templates

Per-supplier `InvoicePdfExtractor` templates for the highest-volume suppliers, each returning an
empty result on a non-matching layout, chained behind the hybrid.

### Task 8: OCR fallback + scan the 2 scanned PDFs

`OcrDocumentTextExtractor implements DocumentTextExtractor` (OpenAI vision through the existing
ChatModel) used when PdfBox returns empty.

### Task 9: Flag persistence

`invoice_ingest_flag` table (append-only) + entity + repository; emit flags from the
enrichment/extraction path (scanned-pdf, pdf-unparseable, pdf-only-line, pdf-csv-mismatch,
missing-pdf).

### Task 10: Filename → invoice-number mapping

Nullable `pdf_filename` on `canonical_invoice`, threaded `CtInvoice → InvoiceInput →
CanonicalInvoice`; authoritative join key in `CtbSftpPull`/`InvoicePdfEnrichmentService`.

### Task 11: UI + reporting

Expose UOM/pack/unit-quantity/WET in the canonical browse + data-explorer; a line-level view
(unit-cost-per-UOM, COGS-by-supplier/WET).

### Task 12: Cleanups from review

Transactional enrichment; `(invoice_number, normalized description)` fallback join when stock_code
absent; keep CSV-authoritative rawRecordId provenance; consolidate `CtInvoicePdfIngestService`.
