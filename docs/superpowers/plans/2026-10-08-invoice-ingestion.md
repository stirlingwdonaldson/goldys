# Invoice Ingestion (CSV-First, Minimal) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Get CTB supplier invoices ingesting so COGS flows, by replacing the stale `CtInvoiceCsvParser` (old column names, headers-only) with one that reads the current export's header **and** line columns, and canonicalizing headers + lines.

**Architecture:** Two changes only — rewrite the parser, and have `CtInvoiceCsvIngestService` canonicalize both the invoice header and each line item. Everything downstream (`canonical_invoice`, `canonical_invoice_line`, `InventoryProjector` → COGS) already exists and needs no change. No new columns, no migration, no new abstractions. This is the **minimal, un-hardened** slice; hardening is deferred until real invoices arrive (see Deferred).

**Tech Stack:** Java 25, Spring Boot 3.5, JUnit 5 + AssertJ + Mockito, Apache Commons CSV, Gradle (`backend/gradlew`). Google-Java-Format via `spotlessApply`.

**Spec:** `docs/superpowers/specs/2026-10-08-invoice-ingestion-design.md` (target state; this plan implements only the CSV path, un-hardened).

## Global Constraints

- **Byte-faithful raw first:** store the CSV via `IngestionService.ingestPush` before parsing. Unchanged.
- **Bitemporal canonical, supersede-not-edit:** reuse the existing `CanonicalInvoiceIngest` / `CanonicalInvoiceLineIngest` facades as-is; no new entities or migrations.
- **Connectors one-way:** no write-back to CTB.
- **COGS is ex-tax line totals:** `InvoiceLineInput.lineTotal` ← CSV `LineTotalExTax` (the COGS driver, summed by `InventoryProjector`). Header totals are never used for COGS.
- **Existing canonical field set is untouched** — `InvoiceInput` / `InvoiceLineInput` records do not change shape; unmapped columns are simply `null`.
- **Minimal, not hardened:** quantity and unit cost are best-effort placeholders; COGS does not depend on them. Fragility is accepted until real invoices arrive.

## Review Focus

1. **Denormalized row shape** — one row per line, header columns repeated; a blank `StockDescription` row must be skipped, never treated as a line. → Task 1.
2. **Re-ingest idempotency** — the same CSV dropped twice must not double-count (the existing supersede-not-edit + `sameFact` short-circuit already guarantees this). → Task 2.
3. **Missing column / empty file** — the parser must fail loudly with `CONNECTOR_SCHEMA_MISMATCH`, not silently produce wrong rows. → Task 1.

---

### Task 1: Rewrite the CSV parser (header + line columns)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceLine.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoice.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvParser.java`
- Test: rewrite `backend/src/test/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvParserTest.java`

**Interfaces:**
- Produces: `CtInvoice` (header fields + `List<CtInvoiceLine> lines`) via `CtInvoiceCsvParser.parse(byte[])`. Used by `CtInvoiceCsvIngestService` (Task 2).

- [ ] **Step 1: Write the failing test (rewrite `CtInvoiceCsvParserTest`)**

```java
package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CtInvoiceCsvParserTest {

  private final CtInvoiceCsvParser parser = new CtInvoiceCsvParser();

  private static final String HEADER =
      "OutletName,SupplierGLCode,InvoiceDueDate,InvoiceTotalExTax,InvoiceFreight,InvoiceFreightExTax,"
          + "SupplierCode,Supplier,Date,Invoice,Total,GST,PONumber,PDF,"
          + "StockCode,StockDescription,LineQuantity,LineUnitCostExTax,LineTotalExTax,LineTaxFlag\n";

  @Test
  void parsesHeaderAndLines() {
    byte[] csv = (HEADER
        + "Goldys,GL-1,2026-10-10,90.91,25.00,22.73,GOLD306600,Bruno's,2026-09-20,INV-2001,117.64,9.09,PO-1,bruno-1956.pdf,"
        + "STK-7,Bruno Premium Lager,\"4 CTN / 48 EACH\",2.50,120.00,true\n"
        + "Goldys,GL-1,2026-10-10,90.91,25.00,22.73,GOLD306600,Bruno's,2026-09-20,INV-2001,117.64,9.09,PO-1,bruno-1956.pdf,"
        + "STK-8,Bruno Pale Ale,1 EACH,3.00,3.00,true\n")
        .getBytes(StandardCharsets.UTF_8);

    CtInvoice invoice = parser.parse(csv);

    assertThat(invoice.invoiceNumber()).isEqualTo("INV-2001");
    assertThat(invoice.supplierName()).isEqualTo("Bruno's");
    assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    assertThat(invoice.dueDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    assertThat(invoice.purchaseNumber()).isEqualTo("PO-1");
    assertThat(invoice.amountExTax()).isEqualByComparingTo(new BigDecimal("90.91"));
    assertThat(invoice.gstAmount()).isEqualByComparingTo(new BigDecimal("9.09"));
    assertThat(invoice.incTaxAmount()).isEqualByComparingTo(new BigDecimal("117.64"));
    assertThat(invoice.lines()).hasSize(2);
    assertThat(invoice.lines().get(0).stockCode()).isEqualTo("STK-7");
    assertThat(invoice.lines().get(0).lineTotalExTax()).isEqualByComparingTo(new BigDecimal("120.00"));
  }

  @Test
  void skipsBlankLineRows() {
    byte[] csv = (HEADER
        + "Goldys,GL-1,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,bruno-1956.pdf,,,,,\n"
        + "Goldys,GL-1,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,bruno-1956.pdf,STK-7,Beer,1 EACH,3.00,3.00,true\n")
        .getBytes(StandardCharsets.UTF_8);

    assertThat(parser.parse(csv).lines()).hasSize(1);
  }

  @Test
  void rejectsAMissingColumn() {
    byte[] csv = "Invoice,Supplier,Date\nINV-1,Bruno's,2026-09-20\n".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("StockDescription");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goldys.platform.connectors.ctb.CtInvoiceCsvParserTest`
Expected: compilation FAIL — `CtInvoice.lines()` / `CtInvoiceLine` do not exist.

- [ ] **Step 3: Write `CtInvoiceLine`**

```java
package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/** A parsed invoice line from CTB's Custom Invoice Export CSV (raw fields only). */
public record CtInvoiceLine(
    String stockCode,          // StockCode
    String description,        // StockDescription
    String rawQuantity,        // LineQuantity (coarse; COGS uses lineTotalExTax, not this)
    BigDecimal unitCostExTax,  // LineUnitCostExTax
    BigDecimal lineTotalExTax) {} // LineTotalExTax — the COGS driver
```

- [ ] **Step 4: Rewrite `CtInvoice`**

```java
package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A parsed invoice from CTB's Custom Invoice Export CSV: header fields plus its line items.
 * The raw CSV bytes are always preserved separately; this holds the fields the canonical model
 * consumes.
 */
public record CtInvoice(
    String supplierName,       // Supplier
    String purchaseNumber,     // PONumber
    LocalDate invoiceDate,     // Date
    String invoiceNumber,      // Invoice
    LocalDate dueDate,         // InvoiceDueDate
    BigDecimal amountExTax,    // InvoiceTotalExTax
    BigDecimal gstAmount,      // GST
    BigDecimal freightAmount,  // InvoiceFreight
    BigDecimal incTaxAmount,   // Total
    List<CtInvoiceLine> lines) {}
```

- [ ] **Step 5: Rewrite `CtInvoiceCsvParser`**

```java
package com.goldys.platform.connectors.ctb;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses CTB's Custom Invoice Export CSV — a single file with header AND line columns, one row per
 * line item, header columns repeated on every row. Rows whose line description is blank are skipped.
 * This replaces the old parser that read only the pre-2026 header-only export.
 */
@Component
public class CtInvoiceCsvParser {

  public CtInvoice parse(byte[] csv) {
    List<CSVRecord> records = readAll(csv);
    if (records.size() < 2) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Invoice CSV is empty");
    }
    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Invoice", "Supplier", "Date", "StockCode", "StockDescription", "LineTotalExTax");

    CSVRecord first = records.get(1);
    List<CtInvoiceLine> lines = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      String description = optionalString(columns, r, "StockDescription");
      if (description == null) {
        continue; // header-only / blank row — not a line
      }
      lines.add(new CtInvoiceLine(
          optionalString(columns, r, "StockCode"),
          description,
          optionalString(columns, r, "LineQuantity"),
          optionalMoney(columns, r, "LineUnitCostExTax"),
          money(columns, r, "LineTotalExTax")));
    }

    return new CtInvoice(
        requireValue(columns, first, "Supplier"),
        optionalString(columns, first, "PONumber"),
        date(columns, first, "Date"),
        requireValue(columns, first, "Invoice"),
        optionalDate(columns, first, "InvoiceDueDate"),
        optionalMoney(columns, first, "InvoiceTotalExTax"),
        optionalMoney(columns, first, "GST"),
        optionalMoney(columns, first, "InvoiceFreight"),
        optionalMoney(columns, first, "Total"),
        lines);
  }

  private static List<CSVRecord> readAll(byte[] csv) {
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      return CSVFormat.DEFAULT.parse(in).getRecords();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Malformed invoice CSV", e);
    }
  }

  private static Map<String, Integer> columnIndex(CSVRecord header) {
    Map<String, Integer> out = new HashMap<>();
    for (int i = 0; i < header.size(); i++) {
      out.put(header.get(i).trim(), i);
    }
    return out;
  }

  private static void require(Map<String, Integer> columns, String... names) {
    for (String name : names) {
      if (!columns.containsKey(name)) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "Missing column '" + name + "' in invoice CSV");
      }
    }
  }

  private static String get(Map<String, Integer> columns, CSVRecord r, String name) {
    Integer i = columns.get(name);
    return i == null ? null : r.get(i);
  }

  private static String requireValue(Map<String, Integer> columns, CSVRecord r, String name) {
    String value = get(columns, r, name);
    if (value == null || value.isBlank()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Missing value for '" + name + "' in invoice CSV");
    }
    return value.trim();
  }

  private static String optionalString(Map<String, Integer> columns, CSVRecord r, String name) {
    return blankToNull(get(columns, r, name));
  }

  private static LocalDate date(Map<String, Integer> columns, CSVRecord r, String name) {
    String value = requireValue(columns, r, name);
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad date '" + value + "' for '" + name + "'", e);
    }
  }

  private static LocalDate optionalDate(Map<String, Integer> columns, CSVRecord r, String name) {
    String value = optionalString(columns, r, name);
    if (value == null) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad date '" + value + "' for '" + name + "'", e);
    }
  }

  private static BigDecimal money(Map<String, Integer> columns, CSVRecord r, String name) {
    String value = requireValue(columns, r, name);
    try {
      return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad amount '" + value + "' for '" + name + "'", e);
    }
  }

  private static BigDecimal optionalMoney(Map<String, Integer> columns, CSVRecord r, String name) {
    String value = blankToNull(get(columns, r, name));
    if (value == null) {
      return null;
    }
    try {
      return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad amount '" + value + "' for '" + name + "'", e);
    }
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests com.goldys.platform.connectors.ctb.CtInvoiceCsvParserTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceLine.java \
        backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoice.java \
        backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvParser.java \
        backend/src/test/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvParserTest.java
git commit -m "feat(invoice): parse current CTB export header + line columns"
```

---

### Task 2: Canonicalize headers + lines in the ingest service

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java`
- Test: Create `backend/src/test/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestServiceTest.java`

**Interfaces:**
- Consumes: `CtInvoiceCsvParser.parse` (Task 1), `CanonicalInvoiceIngest.record(InvoiceInput)` and `CanonicalInvoiceLineIngest.record(InvoiceLineInput)` (existing).
- Produces: unchanged `CtInvoiceCsvIngestService.ingest(byte[])` (the controller keeps working).

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInvoiceIngest;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.InvoiceInput;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.ingestion.IngestionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CtInvoiceCsvIngestServiceTest {

  @Mock IngestionService ingestion;
  @Mock CanonicalInvoiceIngest canonical;
  @Mock CanonicalInvoiceLineIngest lineCanonical;

  private CtInvoiceCsvIngestService service() {
    return new CtInvoiceCsvIngestService(ingestion, new CtInvoiceCsvParser(), canonical, lineCanonical);
  }

  @Test
  void canonicalizesHeaderAndEveryLine() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    byte[] csv = ("Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
        + "INV-1,Bruno's,2026-09-20,STK-7,Beer,1 EACH,120.00\n"
        + "INV-1,Bruno's,2026-09-20,STK-8,Chips,2 EACH,10.00\n")
        .getBytes(StandardCharsets.UTF_8);

    service().ingest(csv);

    ArgumentCaptor<InvoiceInput> inv = ArgumentCaptor.forClass(InvoiceInput.class);
    verify(canonical).record(inv.capture());
    assertThat(inv.getValue().invoiceNumber()).isEqualTo("INV-1");
    assertThat(inv.getValue().supplierName()).isEqualTo("Bruno's");

    ArgumentCaptor<InvoiceLineInput> line = ArgumentCaptor.forClass(InvoiceLineInput.class);
    verify(lineCanonical, times(2)).record(line.capture());
    assertThat(line.getAllValues().get(0).productNameKey()).isEqualTo("beer");
    assertThat(line.getAllValues().get(0).lineTotal()).isEqualByComparingTo(new BigDecimal("120.00"));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goldys.platform.connectors.ctb.CtInvoiceCsvIngestServiceTest`
Expected: compilation FAIL — `CtInvoiceCsvIngestService` constructor signature doesn't match (needs `CanonicalInvoiceLineIngest`).

- [ ] **Step 3: Rewrite `CtInvoiceCsvIngestService`**

```java
package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceIngest;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.InvoiceInput;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for CTB supplier invoices: persist the dropped CSV byte-faithfully, then
 * canonicalize the invoice header and each line item. Line items come from the CSV itself (the
 * export carries both header and line columns), so COGS is available without the PDF path.
 */
@Service
public class CtInvoiceCsvIngestService {
  private final IngestionService ingestion;
  private final CtInvoiceCsvParser parser;
  private final CanonicalInvoiceIngest canonical;
  private final CanonicalInvoiceLineIngest lineCanonical;

  public CtInvoiceCsvIngestService(
      IngestionService ingestion,
      CtInvoiceCsvParser parser,
      CanonicalInvoiceIngest canonical,
      CanonicalInvoiceLineIngest lineCanonical) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.canonical = canonical;
    this.lineCanonical = lineCanonical;
  }

  public void ingest(byte[] csv) {
    UUID rawId = ingestion.ingestPush(
        "CTB", "ctb-invoices", FetchMethod.FILE_EXPORT, "text/csv", csv,
        StandardCharsets.UTF_8.name(), "ctb-invoices");

    CtInvoice invoice = parser.parse(csv);

    canonical.record(new InvoiceInput(
        "CTB",
        invoice.invoiceNumber(),
        invoice.supplierName(),
        invoice.invoiceDate(),
        invoice.dueDate(),
        invoice.incTaxAmount(),
        invoice.purchaseNumber(),
        null, // account number — not in the current export
        null, // tax code — not in the current export
        invoice.amountExTax(),
        invoice.gstAmount(),
        invoice.freightAmount(),
        null, // freight GST — not a direct column in the current export
        rawId));

    int seq = 0;
    for (CtInvoiceLine line : invoice.lines()) {
      seq++;
      lineCanonical.record(new InvoiceLineInput(
          "CTB",
          invoice.invoiceNumber() + ":" + seq,
          invoice.invoiceNumber(),
          invoice.invoiceDate(),
          ProductNameKey.normalize(line.description()),
          quantity(line.rawQuantity()),
          line.unitCostExTax() == null ? BigDecimal.ZERO : line.unitCostExTax(),
          line.lineTotalExTax(),
          null, // category — PDF-only, not in the CSV
          rawId));
    }
  }

  /** Best-effort quantity: the first number in the coarse LineQuantity; 1 when unparseable. */
  private static BigDecimal quantity(String rawQuantity) {
    if (rawQuantity != null) {
      for (String token : rawQuantity.trim().split("\\s+")) {
        try {
          return new BigDecimal(token);
        } catch (NumberFormatException ignored) {
          // keep scanning for the first number
        }
      }
    }
    return BigDecimal.ONE;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.goldys.platform.connectors.ctb.CtInvoiceCsvIngestServiceTest`
Expected: PASS.

- [ ] **Step 5: Run the full suite, then commit**

Run: `./gradlew test`
Expected: PASS (the `CtInvoiceIngestControllerTest` still passes — the controller injects `CtInvoiceCsvIngestService`, whose public `ingest(byte[])` signature is unchanged).

```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java \
        backend/src/test/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestServiceTest.java
git commit -m "feat(invoice): canonicalize invoice header + lines from CSV"
```

---

## Deferred until the invoices are in

Everything below is **harden-later** work, deliberately not in this plan. Do it after real invoices have landed and the actual shapes are known:

- **Structured columns + migration** — `supplier_name_key`, `stock_code`, `uom`, `unit_quantity`, `pack_size` (spec §9.4); nullable `quantity`/`unit_cost`.
- **Shared `ExtractedInvoice` contract + `InvoiceExtractor` port + `InvoiceNormalizer`** (spec §6–§8).
- **Robust quantity parsing** (`QuantityText`), flags (`incomplete-invariant`, `quantity-unparseable`, `foreign-currency`, `missing-pdf`, `scanned-pdf`), and a flag persistence surface (spec §11).
- **SFTP drop + poller** (spec §10).
- **PDF enrichment** (`PdfInvoiceExtractor`, CSV↔PDF join) — Phase 2 (spec §12).
- **Confidence-scored matching + review queue + alias overrides** — Phase 3 (spec §9, §12).
- **Architecture boundary tests.**

## Known assumptions (validate on first real export)

1. **CSV row denormalization** — one row per line item, header columns repeated; blank-line rows skipped.
2. **COGS ex-tax** — `line_total` ← `LineTotalExTax`. Confirm COGS should be ex-GST.
3. **Quantity/unit-cost are placeholders** — COGS depends only on `line_total`; quantity is best-effort first-number and unit cost falls back to zero. Neither feeds a metric yet.
