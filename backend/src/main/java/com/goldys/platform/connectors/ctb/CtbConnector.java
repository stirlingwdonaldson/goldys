package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pulls CTB daily revenue through the authenticated AJAX endpoints, streaming each page to the sink
 * immediately, and aggregates the per-department/per-outlet rows into canonical daily totals.
 */
public class CtbConnector implements SourceConnector {
  private static final int PAGE_SIZE = 200;
  private static final int MAX_PAGES = 250;

  private final CtbClient client;
  private final String email;
  private final String password;
  private final CtbRevenueParser parser;
  private final CanonicalDailySalesIngest canonical;

  public CtbConnector(
      CtbClient client,
      String email,
      String password,
      CtbRevenueParser parser,
      CanonicalDailySalesIngest canonical) {
    this.client = client;
    this.email = email;
    this.password = password;
    this.parser = parser;
    this.canonical = canonical;
  }

  @Override
  public String sourceSystem() {
    return "CTB";
  }

  @Override
  public String connectorName() {
    return "ctb-revenue";
  }

  @Override
  public void fetch(String watermark, IngestionSink sink) {
    client.login(email, password);

    int start = 0;
    int pages = 0;
    int total = -1;
    Map<LocalDate, Totals> byDate = new LinkedHashMap<>();
    Map<LocalDate, UUID> rawByDate = new LinkedHashMap<>();

    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = client.searchRevenues(start, PAGE_SIZE);
      byte[] bytes = page.json().getBytes(StandardCharsets.UTF_8);
      UUID rawId =
          sink.accept(
              new FetchedPayload(
                  FetchMethod.API,
                  "application/json",
                  bytes,
                  StandardCharsets.UTF_8.name(),
                  "ctb-revenue"));

      for (CtbRevenue revenue : parser.parse(bytes)) {
        byDate.computeIfAbsent(revenue.revenueDate(), d -> new Totals()).add(revenue);
        rawByDate.putIfAbsent(revenue.revenueDate(), rawId);
      }

      total = page.totalCount();
      pages++;
      if (start + PAGE_SIZE >= total) {
        break;
      }
      start += PAGE_SIZE;
    }

    for (Map.Entry<LocalDate, Totals> entry : byDate.entrySet()) {
      Totals t = entry.getValue();
      canonical.record(
          new DailySalesInput(
              "CTB", entry.getKey(), t.total, t.gst, t.net, rawByDate.get(entry.getKey())));
    }
  }

  private static final class Totals {
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal gst = BigDecimal.ZERO;
    BigDecimal net = BigDecimal.ZERO;

    void add(CtbRevenue revenue) {
      total = total.add(nz(revenue.totalSales()));
      gst = gst.add(nz(revenue.gstTotal()));
      net = net.add(nz(revenue.kitchenRevenueTotal()));
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
