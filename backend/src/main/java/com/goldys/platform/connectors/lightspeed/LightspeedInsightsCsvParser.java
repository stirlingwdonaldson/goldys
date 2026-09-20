package com.goldys.platform.connectors.lightspeed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses the CSV that Lightspeed Insights embeds in its scheduled-report webhook.
 *
 * <p>The CSV carries a leading row-number column (empty header) and currency-formatted amounts
 * ("$1.46"), both of which are stripped here so callers see clean per-sale rows.
 */
@Component
public class LightspeedInsightsCsvParser {
  // The webhook CSV has a leading row-number column with no header name, which Commons CSV's
  // setHeader() rejects — so the column names are supplied explicitly and the CSV's own header
  // row is skipped.
  private static final String[] HEADERS = {
    "row_number",
    "Reconciliation Date",
    "Reconciliation End Date",
    "Reconciliation Start Date",
    "Sale Opened Date",
    "Sale Type",
    "Sale Number",
    "Order Type",
    "Total Tax",
    "Total Revenue",
    "Total Cost",
    "Sales",
    "Cost Inc Tax",
    "Cost Ex Tax",
    "Total Inc Tax",
    "Total Adjustment Inc Tax",
    "Total Adjustment Tax",
    "Total Adjustment Ex Tax"
  };

  private static final CSVFormat FORMAT =
      CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(true).build();

  public List<LightspeedInsightsSale> parse(byte[] csv) {
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      List<LightspeedInsightsSale> out = new ArrayList<>();
      for (CSVRecord record : FORMAT.parse(in)) {
        out.add(
            new LightspeedInsightsSale(
                LocalDate.parse(record.get("Sale Opened Date")),
                record.get("Sale Number"),
                record.get("Sale Type"),
                money(record.get("Total Inc Tax")),
                money(record.get("Total Tax")),
                money(record.get("Total Adjustment Inc Tax"))));
      }
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static BigDecimal money(String value) {
    if (value == null || value.isBlank()) return null;
    return new BigDecimal(value.replace("$", "").replace(",", "").trim());
  }
}
