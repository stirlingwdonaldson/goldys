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
 * Parses the CSV that Lightspeed Insights embeds in its scheduled-report webhook.
 *
 * <p>The CSV carries a leading row-number column (empty header) and currency-formatted amounts
 * ("$1.46"). The header is read from the CSV itself and the required columns are validated, so a
 * change to the report shape fails loudly instead of silently producing wrong totals.
 */
@Component
public class LightspeedInsightsCsvParser {

  public List<LightspeedInsightsSale> parse(byte[] csv) {
    List<CSVRecord> records = readAll(csv);
    if (records.isEmpty()) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Empty Lightspeed CSV");
    }

    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Sale Opened Date", "Sale Number", "Total Inc Tax", "Total Tax");

    List<LightspeedInsightsSale> out = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      out.add(
          new LightspeedInsightsSale(
              date(r, columns, "Sale Opened Date"),
              r.get(columns.get("Sale Number")),
              get(columns, r, "Sale Type"),
              money(r, columns, "Total Inc Tax"),
              money(r, columns, "Total Tax"),
              money(r, columns, "Total Adjustment Inc Tax")));
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
      String name = header.get(i).trim();
      if (i == 0 && name.isBlank()) {
        name = "row_number";
      }
      out.put(name, i);
    }
    return out;
  }

  private static void require(Map<String, Integer> columns, String... names) {
    for (String name : names) {
      if (!columns.containsKey(name)) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "Missing column '" + name + "' in Lightspeed CSV");
      }
    }
  }

  private static LocalDate date(CSVRecord r, Map<String, Integer> columns, String name) {
    String value = r.get(columns.get(name));
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad date '" + value + "' in Lightspeed CSV", e);
    }
  }

  private static BigDecimal money(CSVRecord r, Map<String, Integer> columns, String name) {
    String value = get(columns, r, name);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad amount '" + value + "' in Lightspeed CSV", e);
    }
  }

  private static String get(Map<String, Integer> columns, CSVRecord r, String name) {
    Integer i = columns.get(name);
    return i == null ? null : r.get(i);
  }
}
