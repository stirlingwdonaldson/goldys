package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.canonical.CanonicalWastageIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.canonical.ProductMappingIngest;
import com.goldys.platform.canonical.SaleRecipeLinkIngest;
import com.goldys.platform.canonical.SaleRecipeLinkInput;
import com.goldys.platform.canonical.SourceProductInput;
import com.goldys.platform.canonical.SourceProductSaleFactIngest;
import com.goldys.platform.canonical.SourceProductSaleFactInput;
import com.goldys.platform.canonical.WastageInput;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pulls CTB daily revenue and per-product sale items through the authenticated AJAX endpoints,
 * streaming each page to the sink immediately and canonicalizing daily totals + per-product totals.
 *
 * <p>The inventory/purchasing endpoints (invoices, recipes, stocks, suppliers, stocktakes, wastage,
 * stock orders, statements, variance, missing-revenue, and reference data) are ingested to the raw
 * ledger only — each under its own {@code fetcherIdentity} so the ledger distinguishes them — with
 * no canonicalization yet. The exception is wastage: the newest {@link #RECENT_WASTAGE_RECORDS}
 * wastage records also have their item lines pulled ({@code ctb-wastage-detail}) and canonicalized
 * every run, so new wastage reaches {@code canonical_wastage} without a manual backfill. See {@code
 * docs/connectors/matching-and-identity.md} for the invoice double-source note and the deferred
 * canonical types.
 */
public class CtbConnector implements SourceConnector {
  private static final Logger log = LoggerFactory.getLogger(CtbConnector.class);
  private static final int PAGE_SIZE = 200;
  private static final int MAX_PAGES = 250;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * How many of the newest wastage records get their item lines re-pulled each run. The header list
   * carries no date (only a free-text description), so "recent" is by id: CTB returns ids newest
   * first. At the current ~3 records a day this is roughly three weeks, which covers edits staff
   * make to recent records while keeping each run to a few dozen small requests. Older records are
   * covered by the one-off {@code ctb-detail-backfill} plus the stock/wastage replay runner.
   */
  static final int RECENT_WASTAGE_RECORDS = 60;

  private final CtbClient client;
  private final String email;
  private final String password;
  private final CtbRevenueParser parser;
  private final CanonicalDailySalesIngest canonical;
  private final CtbSaleItemParser saleItemParser;
  private final CanonicalProductSalesIngest productSales;
  private final CtbSaleRecipeLinkParser saleRecipeLinkParser;
  private final SaleRecipeLinkIngest saleRecipeLinkIngest;
  private final ProductMappingIngest productMapping;
  private final SourceProductSaleFactIngest sourceSaleFact;
  private final CtbWastageParser wastageParser;
  private final CanonicalWastageIngest wastageIngest;
  private final VenueClock venueClock;

  public CtbConnector(
      CtbClient client,
      String email,
      String password,
      CtbRevenueParser parser,
      CanonicalDailySalesIngest canonical,
      CtbSaleItemParser saleItemParser,
      CanonicalProductSalesIngest productSales,
      CtbSaleRecipeLinkParser saleRecipeLinkParser,
      SaleRecipeLinkIngest saleRecipeLinkIngest,
      ProductMappingIngest productMapping,
      SourceProductSaleFactIngest sourceSaleFact,
      CtbWastageParser wastageParser,
      CanonicalWastageIngest wastageIngest,
      VenueClock venueClock) {
    this.client = client;
    this.email = email;
    this.password = password;
    this.parser = parser;
    this.canonical = canonical;
    this.saleItemParser = saleItemParser;
    this.productSales = productSales;
    this.saleRecipeLinkParser = saleRecipeLinkParser;
    this.saleRecipeLinkIngest = saleRecipeLinkIngest;
    this.productMapping = productMapping;
    this.sourceSaleFact = sourceSaleFact;
    this.wastageParser = wastageParser;
    this.wastageIngest = wastageIngest;
    this.venueClock = venueClock;
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

    // Each dataset is attempted independently: one endpoint failing (for example a non-JSON
    // "recipes" response) must not silently drop every dataset that would have been pulled after
    // it. Failures are collected and rethrown at the end, so the run still closes PARTIAL/FAILED
    // and a missing dataset is never mistaken for "no new data".
    Map<String, ConnectorFetchException> failures = new LinkedHashMap<>();

    pull("daily-sales", failures, () -> pullRevenue(watermark, sink));
    pullSaleItems(watermark, sink, failures);

    // Inventory / purchasing / food-cost endpoints — raw ledger only, per fetcher identity.
    pull(
        "ctb-invoices-ajax",
        failures,
        () -> pullRaw("ctb-invoices-ajax", client::searchInvoices, sink));
    pull("ctb-sale-recipe-links", failures, () -> pullSaleRecipeLinks(sink));
    pull("ctb-recipes", failures, () -> pullSingle("ctb-recipes", client::getAllRecipes, sink));
    pull("ctb-stocks", failures, () -> pullRaw("ctb-stocks", client::searchStocks, sink));
    pull(
        "ctb-suppliers",
        failures,
        () -> pullSingle("ctb-suppliers", client::getAllSuppliers, sink));
    pull(
        "ctb-stocktakes",
        failures,
        () -> pullRaw("ctb-stocktakes", client::searchStocktakes, sink));
    pull("ctb-wastage", failures, () -> pullWastage(sink, failures));
    pull(
        "ctb-stock-orders",
        failures,
        () -> pullRaw("ctb-stock-orders", client::searchStockOrders, sink));
    pull("ctb-statements", failures, () -> pullStatements(sink));
    pull("ctb-reference-data", failures, () -> pullReferenceData(sink));

    if (!failures.isEmpty()) {
      throw aggregate(failures);
    }
  }

  /** Attempt one dataset, recording a classified failure instead of aborting the whole pull. */
  private static void pull(
      String dataset, Map<String, ConnectorFetchException> failures, Runnable attempt) {
    try {
      attempt.run();
    } catch (ConnectorFetchException e) {
      log.warn("CTB dataset {} failed: {}: {}", dataset, e.failureType(), e.getMessage());
      failures.put(dataset, e);
    }
  }

  /**
   * A single failed dataset retains its type/cause and includes its dataset/date; several are
   * summarised (with each dataset's own detail) so the run records what is missing rather than only
   * the first, and the captured response evidence is not lost.
   */
  private static ConnectorFetchException aggregate(Map<String, ConnectorFetchException> failures) {
    if (failures.size() == 1) {
      var failure = failures.entrySet().iterator().next();
      return new ConnectorFetchException(
          failure.getValue().failureType(),
          "CTB dataset " + failure.getKey() + ": " + failure.getValue().getMessage(),
          failure.getValue());
    }
    String firstType = failures.values().iterator().next().failureType();
    StringBuilder detail = new StringBuilder("CTB datasets failed:");
    for (Map.Entry<String, ConnectorFetchException> entry : failures.entrySet()) {
      detail
          .append("\n- ")
          .append(entry.getKey())
          .append(": ")
          .append(entry.getValue().failureType())
          .append(": ")
          .append(entry.getValue().getMessage());
    }
    return new ConnectorFetchException(firstType, detail.toString());
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

    throwIfTruncated("daily-sales", pages, start, total);

    for (Map.Entry<LocalDate, Totals> entry : byDate.entrySet()) {
      Totals t = entry.getValue();
      canonical.record(
          new DailySalesInput(
              "CTB", entry.getKey(), t.total, t.gst, t.net, rawByDate.get(entry.getKey())));
    }
  }

  private void pullSaleItems(
      String watermark, IngestionSink sink, Map<String, ConnectorFetchException> failures) {
    // CTB's sale-items endpoint has no per-row date (the date is the range supplied to the
    // call), so pull one day at a time and attribute every row to that day. CTB keeps a
    // rolling ~90 days of sale items, so backfill that window.
    LocalDate to = watermarkDate(watermark);
    LocalDate from = to.minusDays(90);
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      LocalDate requestedDay = day;
      pull("sale-items:" + day, failures, () -> pullSaleItemsForDay(requestedDay, sink));
    }
  }

  private void pullSaleItemsForDay(LocalDate day, IngestionSink sink) {
    int start = 0;
    int pages = 0;
    int total = -1;
    CtbProductSalesBatch batch = new CtbProductSalesBatch(day);
    UUID lastRawId = null;
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page =
          client.searchSaleItems(day.toString(), day.toString(), start, PAGE_SIZE);
      // The sale-item payload carries no date (saleDate is null on every row); the trading day is
      // the request window. Persist it out-of-band so a later replay can recover the date durably.
      UUID rawId = writeRaw(page.json(), "ctb-revenue", "sale-items:" + day, sink);

      lastRawId = rawId;
      for (CtbSaleItem item : saleItemParser.parse(page.json().getBytes(StandardCharsets.UTF_8))) {
        batch.add(item, rawId);
      }

      total = page.totalCount();
      pages++;
      if (start + PAGE_SIZE >= total) {
        break;
      }
      start += PAGE_SIZE;
    }

    throwIfTruncated("sale-items for " + day, pages, start, total);

    List<SourceProductSaleFactInput> facts = batch.sourceFacts();
    if (!facts.isEmpty()) {
      if (facts.stream().allMatch(f -> f.sourceRecordRef().startsWith("ctb-row:")))
        sourceSaleFact.recordReport(facts, lastRawId);
      else sourceSaleFact.recordAll(facts);
      for (SourceProductSaleFactInput fact : facts)
        productMapping.mapSourceProduct(
            new SourceProductInput(
                "CTB", fact.stockCode(), null, fact.productName(), null, null, fact.rawRecordId()));
    }
    productSales.recordAll(batch.totals());
  }

  /** Page the sale-item→recipe links, writing each page raw and recording canonical links once. */
  private void pullSaleRecipeLinks(IngestionSink sink) {
    List<SaleRecipeLinkInput> all = new ArrayList<>();
    int start = 0;
    int pages = 0;
    int total = -1;
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = client.searchDistinctSaleItemsForLinking(start, PAGE_SIZE);
      UUID rawId = writeRaw(page.json(), "ctb-sale-recipe-links", sink);
      all.addAll(saleRecipeLinkParser.parse(page.json().getBytes(StandardCharsets.UTF_8), rawId));
      total = page.totalCount();
      pages++;
      if (start + PAGE_SIZE >= total) {
        break;
      }
      start += PAGE_SIZE;
    }
    throwIfTruncated("ctb-sale-recipe-links", pages, start, total);
    saleRecipeLinkIngest.recordAll(all);
    for (SaleRecipeLinkInput link : all) {
      if (link.saleItemStockCode() != null && !link.saleItemStockCode().isBlank()) {
        productMapping.mapSourceProduct(
            new SourceProductInput(
                "CTB",
                link.saleItemStockCode(),
                null,
                link.saleItemStockDescription(),
                null,
                null,
                link.rawRecordId()));
      }
    }
  }

  /**
   * Pages the wastage headers to the raw ledger ({@code ctb-wastage}), then pulls and canonicalizes
   * the item lines of the newest {@link #RECENT_WASTAGE_RECORDS} records. Each record's detail is
   * its own dataset in {@code failures}, so one bad record closes the run PARTIAL without hiding
   * the others.
   */
  private void pullWastage(IngestionSink sink, Map<String, ConnectorFetchException> failures) {
    List<Integer> ids = new ArrayList<>();
    int start = 0;
    int pages = 0;
    int total = -1;
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = client.searchWastageRecords(start, PAGE_SIZE);
      writeRaw(page.json(), "ctb-wastage", sink);
      ids.addAll(readIds(page.json(), "wastageRecordId"));
      total = page.totalCount();
      pages++;
      if (start + PAGE_SIZE >= total) {
        break;
      }
      start += PAGE_SIZE;
    }
    throwIfTruncated("ctb-wastage", pages, start, total);

    // Sort explicitly rather than relying on CTB's response order.
    List<Integer> recent =
        ids.stream()
            .distinct()
            .sorted(Comparator.reverseOrder())
            .limit(RECENT_WASTAGE_RECORDS)
            .toList();
    for (int id : recent) {
      pull("ctb-wastage-detail#" + id, failures, () -> pullWastageDetail(id, sink));
    }
  }

  /**
   * Pulls every page of one wastage record's lines raw-first (source ref {@code wastage:<id>}, the
   * same identity the backfill uses), then records the parsed lines through the canonical facade.
   * The facade is idempotent and supersedes per (record, line), so re-pulling daily is safe.
   */
  private void pullWastageDetail(int wastageRecordId, IngestionSink sink) {
    List<WastageInput> lines = new ArrayList<>();
    int start = 0;
    int pages = 0;
    int total = -1;
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = client.getWastageRecordDetail(wastageRecordId, start, PAGE_SIZE);
      UUID rawId = writeRaw(page.json(), "ctb-wastage-detail", "wastage:" + wastageRecordId, sink);
      lines.addAll(wastageParser.parse(page.json().getBytes(StandardCharsets.UTF_8), rawId));
      total = page.totalCount();
      pages++;
      if (start + PAGE_SIZE >= total) {
        break;
      }
      start += PAGE_SIZE;
    }
    throwIfTruncated("ctb-wastage-detail for " + wastageRecordId, pages, start, total);
    wastageIngest.recordAll(lines);
  }

  private static List<Integer> readIds(String json, String field) {
    try {
      JsonNode data = MAPPER.readTree(json).path("data");
      List<Integer> ids = new ArrayList<>();
      if (data.isArray()) {
        for (JsonNode item : data) {
          JsonNode id = item.get(field);
          if (id != null && id.canConvertToInt()) {
            ids.add(id.asInt());
          }
        }
      }
      return ids;
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response", e);
    }
  }

  /**
   * Supplier statements use a rolling 12-month window. Single-shot: {@code SearchStatement} returns
   * the full list in one response and ignores {@code start}/{@code limit}, so it must not be paged.
   */
  private void pullStatements(IngestionSink sink) {
    LocalDate end = venueClock.today();
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

    throwIfTruncated(fetcherIdentity, pages, start, total);
  }

  /** Pull a single-response endpoint (reference data, {@code GetAll*}). */
  private void pullSingle(
      String fetcherIdentity, Supplier<CtbClient.CtbPage> pageFn, IngestionSink sink) {
    writeRaw(pageFn.get().json(), fetcherIdentity, sink);
  }

  /**
   * Throws a classified failure when a paging loop stopped because it hit {@link #MAX_PAGES} while
   * the source's {@code totalCount} still reports more rows — a silent truncation that would
   * otherwise look like a complete pull. The pages already persisted stay in the ledger, and the
   * caller's per-dataset {@code pull(...)} collection closes the run {@code PARTIAL}.
   */
  private static void throwIfTruncated(String dataset, int pages, int start, int total) {
    if (pages >= MAX_PAGES && start + PAGE_SIZE < total) {
      throw new ConnectorFetchException(
          "CONNECTOR_TRUNCATED",
          dataset + " truncated after " + MAX_PAGES + " pages (" + total + " rows reported)");
    }
  }

  /** Store one raw page byte-faithfully and return its ledger id. */
  private UUID writeRaw(String json, String fetcherIdentity, IngestionSink sink) {
    return writeRaw(json, fetcherIdentity, null, sink);
  }

  /**
   * Store one raw page byte-faithfully with an out-of-band source-record reference (e.g. the
   * sale-items request day), and return its ledger id.
   */
  private UUID writeRaw(
      String json, String fetcherIdentity, String sourceRecordRef, IngestionSink sink) {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    return sink.accept(
        new FetchedPayload(
            FetchMethod.API,
            "application/json",
            bytes,
            StandardCharsets.UTF_8.name(),
            fetcherIdentity,
            sourceRecordRef));
  }

  private LocalDate watermarkDate(String watermark) {
    if (watermark != null && !watermark.isBlank()) {
      try {
        return LocalDate.parse(watermark);
      } catch (DateTimeParseException e) {
        // not a date — fall through to today
      }
    }
    return venueClock.today();
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
