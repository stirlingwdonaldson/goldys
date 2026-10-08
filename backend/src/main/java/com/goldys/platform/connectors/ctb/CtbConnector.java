package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.ProductSalesInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Pulls CTB daily revenue and per-product sale items through the authenticated AJAX endpoints,
 * streaming each page to the sink immediately and canonicalizing daily totals + per-product totals.
 *
 * <p>The inventory/purchasing endpoints (invoices, recipes, stocks, suppliers, stocktakes, wastage,
 * stock orders, statements, variance, missing-revenue, and reference data) are ingested to the raw
 * ledger only — each under its own {@code fetcherIdentity} so the ledger distinguishes them — with
 * no canonicalization yet. See {@code docs/connectors/matching-and-identity.md} for the invoice
 * double-source note and the deferred canonical types.
 */
public class CtbConnector implements SourceConnector {
  private static final int PAGE_SIZE = 200;
  private static final int MAX_PAGES = 250;

  private final CtbClient client;
  private final String email;
  private final String password;
  private final CtbRevenueParser parser;
  private final CanonicalDailySalesIngest canonical;
  private final CtbSaleItemParser saleItemParser;
  private final CanonicalProductSalesIngest productSales;

  public CtbConnector(
      CtbClient client,
      String email,
      String password,
      CtbRevenueParser parser,
      CanonicalDailySalesIngest canonical,
      CtbSaleItemParser saleItemParser,
      CanonicalProductSalesIngest productSales) {
    this.client = client;
    this.email = email;
    this.password = password;
    this.parser = parser;
    this.canonical = canonical;
    this.saleItemParser = saleItemParser;
    this.productSales = productSales;
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

    pullRevenue(watermark, sink);
    pullSaleItems(watermark, sink);

    // Inventory / purchasing / food-cost endpoints — raw ledger only, per fetcher identity.
    pullRaw("ctb-invoices-ajax", client::searchInvoices, sink);
    pullRaw("ctb-sale-recipe-links", client::searchDistinctSaleItemsForLinking, sink);
    pullSingle("ctb-recipes", client::getAllRecipes, sink);
    pullRaw("ctb-stocks", client::searchStocks, sink);
    pullSingle("ctb-suppliers", client::getAllSuppliers, sink);
    pullRaw("ctb-stocktakes", client::searchStocktakes, sink);
    pullRaw("ctb-wastage", client::searchWastageRecords, sink);
    pullRaw("ctb-stock-orders", client::searchStockOrders, sink);
    pullStatements(sink);
    pullReferenceData(sink);
  }

  private void pullRevenue(String watermark, IngestionSink sink) {
    LocalDate since = watermark == null || watermark.isBlank() ? null : LocalDate.parse(watermark);

    int start = 0;
    int pages = 0;
    int total = -1;
    Map<LocalDate, Totals> byDate = new LinkedHashMap<>();
    Map<LocalDate, UUID> rawByDate = new LinkedHashMap<>();

    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = client.searchRevenues(start, PAGE_SIZE);
      UUID rawId = writeRaw(page.json(), "ctb-revenue", sink);

      for (CtbRevenue revenue : parser.parse(page.json().getBytes(StandardCharsets.UTF_8))) {
        if (since != null && revenue.revenueDate().isBefore(since)) {
          continue;
        }
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

  private void pullSaleItems(String watermark, IngestionSink sink) {
    // CTB's sale-items endpoint has no per-row date (the date is the range supplied to the
    // call), so pull one day at a time and attribute every row to that day. CTB keeps a
    // rolling ~90 days of sale items, so backfill that window.
    LocalDate to = watermarkDate(watermark);
    LocalDate from = to.minusDays(90);
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      pullSaleItemsForDay(day, sink);
    }
  }

  private void pullSaleItemsForDay(LocalDate day, IngestionSink sink) {
    int start = 0;
    int pages = 0;
    int total = -1;
    Map<String, ProductSalesInput> byProduct = new LinkedHashMap<>();
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page =
          client.searchSaleItems(day.toString(), day.toString(), start, PAGE_SIZE);
      UUID rawId = writeRaw(page.json(), "ctb-revenue", sink);

      for (CtbSaleItem item : saleItemParser.parse(page.json().getBytes(StandardCharsets.UTF_8))) {
        String key = ProductNameKey.normalize(item.stockDescription());
        byProduct.merge(
            key,
            new ProductSalesInput("CTB", day, key, item.quantitySold(), item.amount(), rawId),
            (a, b) ->
                new ProductSalesInput(
                    "CTB",
                    day,
                    key,
                    a.quantitySold().add(b.quantitySold()),
                    a.amount().add(b.amount()),
                    a.rawRecordId()));
      }

      total = page.totalCount();
      pages++;
      if (start + PAGE_SIZE >= total) {
        break;
      }
      start += PAGE_SIZE;
    }

    for (ProductSalesInput input : byProduct.values()) {
      productSales.record(input);
    }
  }

  /**
   * Supplier statements use a rolling 12-month window. Single-shot: {@code SearchStatement} returns
   * the full list in one response and ignores {@code start}/{@code limit}, so it must not be paged.
   */
  private void pullStatements(IngestionSink sink) {
    LocalDate end = LocalDate.now();
    LocalDate start = end.minusMonths(12);
    pullSingle(
        "ctb-statements", () -> client.getStatements(start.toString(), end.toString()), sink);
  }

  private void pullReferenceData(IngestionSink sink) {
    pullSingle("ctb-reference-data", client::getAllDepartments, sink);
    pullSingle("ctb-reference-data", client::getAllActivities, sink);
    pullSingle("ctb-reference-data", client::getAllStockCategories, sink);
    pullSingle("ctb-reference-data", client::getAllUoms, sink);
    pullSingle("ctb-reference-data", client::getAllSupplierMeasurements, sink);
    pullSingle("ctb-reference-data", client::getAllMeasurementConversions, sink);
  }

  /** Page through a paged-list endpoint ({@code start}/{@code limit}/{@code totalCount}). */
  private void pullRaw(
      String fetcherIdentity,
      BiFunction<Integer, Integer, CtbClient.CtbPage> pageFn,
      IngestionSink sink) {
    int start = 0;
    int pages = 0;
    int total = -1;
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = pageFn.apply(start, PAGE_SIZE);
      writeRaw(page.json(), fetcherIdentity, sink);
      total = page.totalCount();
      pages++;
      if (start + PAGE_SIZE >= total) {
        break;
      }
      start += PAGE_SIZE;
    }
  }

  /** Pull a single-response endpoint (reference data, {@code GetAll*}). */
  private void pullSingle(
      String fetcherIdentity, Supplier<CtbClient.CtbPage> pageFn, IngestionSink sink) {
    writeRaw(pageFn.get().json(), fetcherIdentity, sink);
  }

  /** Store one raw page byte-faithfully and return its ledger id. */
  private UUID writeRaw(String json, String fetcherIdentity, IngestionSink sink) {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    return sink.accept(
        new FetchedPayload(
            FetchMethod.API,
            "application/json",
            bytes,
            StandardCharsets.UTF_8.name(),
            fetcherIdentity));
  }

  private static LocalDate watermarkDate(String watermark) {
    if (watermark != null && !watermark.isBlank()) {
      try {
        return LocalDate.parse(watermark);
      } catch (DateTimeParseException e) {
        // not a date — fall through to today
      }
    }
    return LocalDate.now();
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
