package com.goldys.platform.connectors.ctb;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses CTB's Custom Invoice Export CSV — a single file with header AND line columns, one row per
 * line item, header columns repeated on every row. Rows are grouped by invoice number, so one file
 * can carry many invoices. Rows whose line description is blank are skipped. This replaces the old
 * parser that read only the pre-2026 header-only export.
 */
@Component
public class CtInvoiceCsvParser {

  public List<CtInvoice> parse(byte[] csv) {
    List<CSVRecord> records = readAll(csv);
    if (records.size() < 2) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Invoice CSV is empty");
    }
    Map<String, Integer> columns = columnIndex(records.get(0));
    require(
        columns, "Invoice", "Supplier", "Date", "StockCode", "StockDescription", "LineTotalExTax");

    Map<String, Group> groups = new LinkedHashMap<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      String description = optionalString(columns, r, "StockDescription");
      if (description == null) {
        continue; // header-only / blank row — not a line
      }
      String invoiceNumber = requireValue(columns, r, "Invoice");
      Group g =
          groups.computeIfAbsent(
              invoiceNumber,
              k ->
                  new Group(
                      requireValue(columns, r, "Supplier"),
                      optionalString(columns, r, "PONumber"),
                      date(columns, r, "Date"),
                      k,
                      optionalDate(columns, r, "InvoiceDueDate"),
                      optionalMoney(columns, r, "InvoiceTotalExTax"),
                      optionalMoney(columns, r, "GST"),
                      optionalMoney(columns, r, "InvoiceFreight"),
                      optionalMoney(columns, r, "Total")));
      g.lines.add(
          new CtInvoiceLine(
              optionalString(columns, r, "StockCode"),
              description,
              optionalString(columns, r, "LineQuantity"),
              optionalMoney(columns, r, "LineUnitCostExTax"),
              money(columns, r, "LineTotalExTax")));
    }

    List<CtInvoice> out = new ArrayList<>();
    for (Group g : groups.values()) {
      out.add(
          new CtInvoice(
              g.supplierName,
              g.purchaseNumber,
              g.invoiceDate,
              g.invoiceNumber,
              g.dueDate,
              g.amountExTax,
              g.gstAmount,
              g.freightAmount,
              g.incTaxAmount,
              g.lines));
    }
    return out;
  }

  /** Header fields captured from the first line row of an invoice, plus its accumulated lines. */
  private static final class Group {
    final String supplierName;
    final String purchaseNumber;
    final LocalDate invoiceDate;
    final String invoiceNumber;
    final LocalDate dueDate;
    final BigDecimal amountExTax;
    final BigDecimal gstAmount;
    final BigDecimal freightAmount;
    final BigDecimal incTaxAmount;
    final List<CtInvoiceLine> lines = new ArrayList<>();

    Group(
        String supplierName,
        String purchaseNumber,
        LocalDate invoiceDate,
        String invoiceNumber,
        LocalDate dueDate,
        BigDecimal amountExTax,
        BigDecimal gstAmount,
        BigDecimal freightAmount,
        BigDecimal incTaxAmount) {
      this.supplierName = supplierName;
      this.purchaseNumber = purchaseNumber;
      this.invoiceDate = invoiceDate;
      this.invoiceNumber = invoiceNumber;
      this.dueDate = dueDate;
      this.amountExTax = amountExTax;
      this.gstAmount = gstAmount;
      this.freightAmount = freightAmount;
      this.incTaxAmount = incTaxAmount;
    }
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

  /**
   * CTB exports dates as d/M/yyyy (e.g. 1/05/2026, day and month unpadded); ISO is also accepted.
   */
  private static final DateTimeFormatter CTB_DATE = DateTimeFormatter.ofPattern("d/M/yyyy");

  private static LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (DateTimeParseException ignored) {
      return LocalDate.parse(value, CTB_DATE);
    }
  }

  private static LocalDate date(Map<String, Integer> columns, CSVRecord r, String name) {
    String value = requireValue(columns, r, name);
    try {
      return parseDate(value);
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
      return parseDate(value);
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
