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
 * Parses the CSV that Lightspeed Insights embeds in its "sales-details" scheduled-report webhook.
 *
 * <p>Columns are located by header display name, never by position: the {@code query.fields} order
 * in the Looker envelope does not match the CSV column order, and the report must be delivered
 * without the {@code product_salelines.product_id} field (that join silently drops ~92% of rows). A
 * missing required column fails loudly.
 */
@Component
public class LightspeedSaleItemCsvParser {

  public List<LightspeedSaleItem> parse(byte[] csv) {
    List<CSVRecord> records = readAll(csv);
    if (records.isEmpty()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Empty Lightspeed sale-items CSV");
    }

    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Sales Data Receipt Line ID", "Sales Data Sale Closed Date");

    List<LightspeedSaleItem> out = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      if (text(r, columns, "Sales Data Receipt Line ID") == null) {
        continue; // trailing/blank row
      }
      out.add(
          new LightspeedSaleItem(
              text(r, columns, "Sales Data Receipt Line ID"),
              date(r, columns, "Sales Data Sale Closed Date"),
              text(r, columns, "Saleline Payments Sale Number"),
              text(r, columns, "Products Product Name"),
              text(r, columns, "Products Product Number"),
              text(r, columns, "Products SKU"),
              text(r, columns, "Products POS Category Name"),
              integer(r, columns, "Sales Data Product Quantity"),
              money(r, columns, "Sales Data Total Inc Tax"),
              money(r, columns, "Advanced Dimensions Sold Price Inc Tax"),
              money(r, columns, "Sales Data Total Tax"),
              money(r, columns, "Sales Data Cost Inc Tax"),
              text(r, columns, "Sales Data Order Type"),
              text(r, columns, "Sales Data Sale Type"),
              text(r, columns, "Staff Sale Closed Staff Name"),
              text(r, columns, "Register Closed Register Name"),
              text(r, columns, "Tables Table Number")));
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
            "Missing column '" + name + "' in Lightspeed sale-items CSV");
      }
    }
  }

  private static LocalDate date(CSVRecord r, Map<String, Integer> columns, String name) {
    String value = text(r, columns, name);
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad date '" + value + "' in Lightspeed sale-items CSV", e);
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
          "Bad amount '" + value + "' in Lightspeed sale-items CSV",
          e);
    }
  }

  private static Integer integer(CSVRecord r, Map<String, Integer> columns, String name) {
    String value = text(r, columns, name);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad count '" + value + "' in Lightspeed sale-items CSV", e);
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
