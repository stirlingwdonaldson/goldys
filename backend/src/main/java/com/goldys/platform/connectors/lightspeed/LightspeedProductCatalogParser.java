package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses the Lightspeed product-catalog CSV drop (the menu export's product table) into {@link
 * LightspeedProduct} rows. The header is read from the CSV and the required columns are validated,
 * so a report-shape change fails loudly rather than silently producing wrong rows.
 */
@Component
public class LightspeedProductCatalogParser {

  public List<LightspeedProduct> parse(byte[] csv) {
    List<CSVRecord> records = readAll(stripBom(csv));
    if (records.size() < 2) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Lightspeed product-catalog CSV is empty");
    }

    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "ProductID", "ProductName");

    List<LightspeedProduct> out = new ArrayList<>();
    int width = columns.size();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      if (r.size() != width) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH",
            "Lightspeed product-catalog CSV row has wrong column count");
      }
      out.add(row(r, columns));
    }
    return out;
  }

  private LightspeedProduct row(CSVRecord r, Map<String, Integer> c) {
    return new LightspeedProduct(
        parseInt(requireValue(c, r, "ProductID"), "ProductID"),
        requireValue(c, r, "ProductName"),
        blankToNull(get(c, r, "CategoryNames")),
        price(get(c, r, "SellPriceIncTax")),
        "1".equals(blankToNull(get(c, r, "IsModifier"))),
        blankToNull(get(c, r, "ReportingGroup")));
  }

  private BigDecimal price(String value) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    // Prices carry the currency symbol and thousands grouping, e.g. "$1,234.50".
    String cleaned = v.replace("$", "").replace(",", "").trim();
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH",
          "Bad price '" + value + "' in Lightspeed product-catalog CSV",
          e);
    }
  }

  private static int parseInt(String value, String column) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH",
          "Bad " + column + " '" + value + "' in Lightspeed product-catalog CSV",
          e);
    }
  }

  private static byte[] stripBom(byte[] csv) {
    if (csv.length >= 3
        && (csv[0] & 0xFF) == 0xEF
        && (csv[1] & 0xFF) == 0xBB
        && (csv[2] & 0xFF) == 0xBF) {
      return Arrays.copyOfRange(csv, 3, csv.length);
    }
    return csv;
  }

  private static List<CSVRecord> readAll(byte[] csv) {
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      return CSVFormat.DEFAULT.parse(in).getRecords();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Malformed Lightspeed product-catalog CSV", e);
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
            "Missing column '" + name + "' in Lightspeed product-catalog CSV");
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
          "CONNECTOR_SCHEMA_MISMATCH",
          "Missing value for '" + name + "' in Lightspeed product-catalog CSV");
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
