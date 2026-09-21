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
 * Parses the per-product CSV the scheduled Lightspeed report delivers (the "Sales By" export).
 *
 * <p>The header is read from the CSV itself and the required columns are validated, so a change to
 * the report shape fails loudly instead of silently misparsing.
 */
@Component
public class LightspeedProductCsvParser {

  public List<LightspeedProductSale> parse(byte[] csv, LocalDate tradingDate) {
    List<CSVRecord> records = readAll(csv);
    if (records.isEmpty()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Empty Lightspeed product CSV");
    }
    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Product", "Quantity", "Sale Amount");

    List<LightspeedProductSale> out = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      String name = get(columns, r, "Product");
      if (name == null || name.isBlank()) continue;
      out.add(
          new LightspeedProductSale(
              tradingDate,
              name,
              money(get(columns, r, "Quantity")),
              money(get(columns, r, "Sale Amount"))));
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
            "CONNECTOR_SCHEMA_MISMATCH", "Missing column '" + name + "' in Lightspeed product CSV");
      }
    }
  }

  private static String get(Map<String, Integer> columns, CSVRecord r, String name) {
    Integer i = columns.get(name);
    return i == null ? null : r.get(i);
  }

  private static BigDecimal money(String value) {
    if (value == null || value.isBlank()) return BigDecimal.ZERO;
    try {
      return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad amount '" + value + "'", e);
    }
  }
}
