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
 * Parses the CTB custom invoice-export CSV into invoice metadata rows. The header is read from the
 * CSV and the required columns are validated, so a report-shape change fails loudly rather than
 * silently producing wrong rows. Columns are provisional pending the first real export.
 */
@Component
public class CtInvoiceCsvParser {

  public List<CtInvoice> parse(byte[] csv) {
    List<CSVRecord> records = readAll(csv);
    if (records.size() < 2) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Invoice CSV is empty");
    }
    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Supplier", "Invoice Number", "Invoice Date", "Due Date", "Total");

    List<CtInvoice> out = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      String invoiceNumber = requireValue(columns, r, "Invoice Number");
      String supplier = requireValue(columns, r, "Supplier");
      LocalDate invoiceDate = date(columns, r, "Invoice Date");
      LocalDate dueDate = optionalDate(columns, r, "Due Date");
      BigDecimal total = optionalDecimal(columns, r, "Total");
      out.add(new CtInvoice(supplier, invoiceNumber, invoiceDate, dueDate, total));
    }
    return out;
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
    String value = blankToNull(get(columns, r, name));
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

  private static BigDecimal optionalDecimal(
      Map<String, Integer> columns, CSVRecord r, String name) {
    String value = blankToNull(get(columns, r, name));
    if (value == null) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad number '" + value + "' for '" + name + "'", e);
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

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
