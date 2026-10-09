package com.goldys.platform.connectors.lightspeed;

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
 * Parses the CSV that Lightspeed Insights embeds in its "all-deleted-orders" scheduled-report
 * webhook.
 *
 * <p>Columns are located by header display name, never by position (see the design spec). A missing
 * required column fails loudly.
 */
@Component
public class LightspeedDeletedSaleCsvParser {

  public List<LightspeedDeletedSale> parse(byte[] csv) {
    List<CSVRecord> records = readAll(csv);
    if (records.isEmpty()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Empty Lightspeed deleted-sales CSV");
    }

    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Deleted Orders Order Opened Date", "Sales Data Sale Number");

    List<LightspeedDeletedSale> out = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      if (text(r, columns, "Sales Data Sale Number") == null) {
        continue; // trailing/blank row
      }
      out.add(
          new LightspeedDeletedSale(
              date(r, columns, "Deleted Orders Order Opened Date"),
              text(r, columns, "Sales Data Sale Number"),
              text(r, columns, "Deleted Orders Order Type"),
              text(r, columns, "Sales Data Note"),
              money(r, columns, "Deleted Orders Total Inc Tax"),
              money(r, columns, "Deleted Orders Total Ex Tax"),
              money(r, columns, "Deleted Orders Total Tax"),
              money(r, columns, "Sales Data Total Cost"),
              text(r, columns, "Register Opened Register Code"),
              text(r, columns, "Register Opened Register Name"),
              text(r, columns, "Register Deleted Register Code"),
              text(r, columns, "Register Deleted Register Name"),
              text(r, columns, "Staff Staff Name"),
              text(r, columns, "Staff Staff Code"),
              text(r, columns, "Staff Order Deleted Staff Name"),
              text(r, columns, "Staff Order Deleted Staff Code"),
              text(r, columns, "Tables Table Number"),
              text(r, columns, "Site Numeric ID"),
              text(r, columns, "Customer Name")));
    }
    return out;
  }

  private static List<CSVRecord> readAll(byte[] csv) {
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      return CSVFormat.DEFAULT.parse(in).getRecords();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
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
            "CONNECTOR_SCHEMA_MISMATCH",
            "Missing column '" + name + "' in Lightspeed deleted-sales CSV");
      }
    }
  }

  private static LocalDate date(CSVRecord r, Map<String, Integer> columns, String name) {
    String value = text(r, columns, name);
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH",
          "Bad date '" + value + "' in Lightspeed deleted-sales CSV",
          e);
    }
  }

  private static BigDecimal money(CSVRecord r, Map<String, Integer> columns, String name) {
    String value = text(r, columns, name);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH",
          "Bad amount '" + value + "' in Lightspeed deleted-sales CSV",
          e);
    }
  }

  private static String text(CSVRecord r, Map<String, Integer> columns, String name) {
    Integer i = columns.get(name);
    if (i == null) {
      return null;
    }
    String v = r.get(i);
    return v == null || v.isBlank() ? null : v;
  }
}
