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
 * Parses the CTB Custom Invoice Export CSV into invoice metadata rows.
 *
 * <p>The export header is read from the CSV itself and the identity columns are validated, so a
 * report-shape change fails loudly rather than silently producing wrong rows. Column names are the
 * literal CTB export headers (note {@code Co./Last Name}, {@code Purchase#}, {@code Supplier
 * Invoice #}, {@code Account #}). Monetary cells are currency-formatted ("$1,234.56") and parsed
 * accordingly.
 *
 * <p>The required identity columns are {@code Co./Last Name}, {@code Supplier Invoice #} and {@code
 * Date}; the tax/freight breakdown and account/purchase numbers are optional and parse to {@code
 * null} when blank.
 */
@Component
public class CtInvoiceCsvParser {

  public List<CtInvoice> parse(byte[] csv) {
    List<CSVRecord> records = readAll(csv);
    if (records.size() < 2) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Invoice CSV is empty");
    }
    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Co./Last Name", "Supplier Invoice #", "Date");

    List<CtInvoice> out = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      out.add(
          new CtInvoice(
              requireValue(columns, r, "Co./Last Name"),
              optionalString(columns, r, "Purchase#"),
              date(columns, r, "Date"),
              requireValue(columns, r, "Supplier Invoice #"),
              optionalString(columns, r, "Account #"),
              optionalMoney(columns, r, "Amount"),
              optionalString(columns, r, "Tax Code"),
              optionalMoney(columns, r, "GST Amount"),
              optionalMoney(columns, r, "Freight Amount"),
              optionalMoney(columns, r, "Freight GST Amount"),
              optionalMoney(columns, r, "Inc-Tax Amount")));
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

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
