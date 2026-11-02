package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * The verified sale-closed-date daily report; reconciliation-date reports cannot write headlines.
 */
@Component
public class LightspeedInsightsCsvParser {
  private static final String DATE = "Sales Data Sale Closed Date";
  private static final String SITE = "Site Numeric ID";
  private static final String NET = "Sales Data Total Ex Tax";
  private static final String GROSS = "Sales Data Total Inc Tax";
  private static final String TAX = "Sales Data Total Tax";
  private static final String ADJUSTMENT = "Sales Data Total Adjustment Inc Tax";
  private static final Set<String> HEADERS = Set.of(DATE, SITE, NET, GROSS, TAX, ADJUSTMENT);

  public List<LightspeedInsightsSale> parse(byte[] csv) {
    var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
    try (var input = new InputStreamReader(new ByteArrayInputStream(csv), decoder);
        var parser = CSVFormat.DEFAULT.parse(input)) {
      var rows = parser.iterator();
      if (!rows.hasNext()) throw mismatch("Daily sales CSV has no header");
      Map<String, Integer> columns = columns(rows.next());
      List<LightspeedInsightsSale> result = new ArrayList<>();
      Set<LocalDate> dates = new HashSet<>();
      while (rows.hasNext()) {
        CSVRecord row = rows.next();
        if (row.size() != HEADERS.size()) throw mismatch("Daily sales row has unexpected columns");
        LocalDate date;
        try {
          date = LocalDate.parse(row.get(columns.get(DATE)));
          // Daily sales identity has no site component: this contract is for Goldy's single venue.
          if (Long.parseLong(row.get(columns.get(SITE))) != 96181)
            throw mismatch("Daily sales report must contain only site 96181");
        } catch (java.time.DateTimeException | NumberFormatException e) {
          throw mismatch("Daily sales row has an invalid date or site");
        }
        if (!dates.add(date))
          throw mismatch("Daily sales report must have one row per closed date");
        BigDecimal net = money(row, columns, NET);
        BigDecimal gross = money(row, columns, GROSS);
        BigDecimal tax = money(row, columns, TAX);
        if (net.add(tax).compareTo(gross) != 0)
          throw mismatch("Daily sales net plus tax must equal gross");
        result.add(
            new LightspeedInsightsSale(date, net, gross, tax, money(row, columns, ADJUSTMENT)));
      }
      return result;
    } catch (IOException | java.io.UncheckedIOException e) {
      throw mismatch("Malformed daily sales CSV");
    }
  }

  private static Map<String, Integer> columns(CSVRecord header) {
    Map<String, Integer> columns = new HashMap<>();
    for (int i = 0; i < header.size(); i++) {
      String name = header.get(i).trim();
      if (name.contains("Reconciliation"))
        throw new ConnectorFetchException(
            "UNSAFE_DAILY_SALES_BASIS",
            "Reconciliation dates cannot identify sale-closed daily totals; use the verified sale-date report");
      if (columns.put(name, i) != null) throw mismatch("Duplicate daily sales CSV header");
    }
    if (!columns.keySet().equals(HEADERS))
      throw mismatch("Daily sales requires exactly the six verified sale-date and site headers");
    return columns;
  }

  private static BigDecimal money(CSVRecord row, Map<String, Integer> columns, String name) {
    try {
      BigDecimal amount =
          new BigDecimal(row.get(columns.get(name)).replace("$", "").replace(",", "").trim())
              .setScale(4, RoundingMode.UNNECESSARY);
      if (amount.precision() > 14) throw new ArithmeticException();
      return amount;
    } catch (ArithmeticException | NumberFormatException e) {
      throw mismatch("Daily sales contains missing or invalid money");
    }
  }

  private static ConnectorFetchException mismatch(String message) {
    return new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", message);
  }
}
