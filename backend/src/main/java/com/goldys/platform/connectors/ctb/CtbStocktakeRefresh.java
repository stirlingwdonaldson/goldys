package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.*;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

/** Refreshes stocktake headers and their dated count lines inside the existing CTB source lock. */
@Component
public class CtbStocktakeRefresh {
  private static final int PAGE_SIZE = 200;
  private static final int MAX_PAGES = 250;
  private final CtbStocktakeParser parser;
  private final CanonicalStocktakeIngest headers;
  private final CanonicalStockCountIngest counts;

  public CtbStocktakeRefresh(
      CtbStocktakeParser parser,
      CanonicalStocktakeIngest headers,
      CanonicalStockCountIngest counts) {
    this.parser = parser;
    this.headers = headers;
    this.counts = counts;
  }

  public void fetch(CtbClient client, IngestionSink sink) {
    Map<Integer, StocktakeInput> stocktakes = new LinkedHashMap<>();
    int total = -1;
    for (int pageIndex = 0; pageIndex < MAX_PAGES; pageIndex++) {
      int start = pageIndex * PAGE_SIZE;
      var page = client.searchStocktakes(start, PAGE_SIZE);
      UUID raw = write(sink, page, "ctb-stocktakes", null);
      var parsed = parser.parseStocktakes(page.json().getBytes(StandardCharsets.UTF_8), raw);
      checkPage(page, total, start, parsed.size());
      total = page.totalCount();
      for (var header : parsed) {
        if (stocktakes.putIfAbsent(header.stocktakeId(), header) != null)
          throw new ConnectorFetchException(
              "CONNECTOR_TRUNCATED", "Stocktake pages repeat a header");
      }
      if (start + PAGE_SIZE >= total) break;
    }
    if (stocktakes.size() != total)
      throw new ConnectorFetchException(
          "CONNECTOR_TRUNCATED", "Stocktake header population is incomplete");
    List<ConnectorFetchException> failures = new ArrayList<>();
    for (var header : stocktakes.values()) {
      headers.record(header);
      try {
        detail(client, sink, header);
      } catch (ConnectorFetchException e) {
        failures.add(
            new ConnectorFetchException(
                e.failureType(), "stocktake:" + header.stocktakeId() + ": " + e.getMessage(), e));
      }
    }
    if (!failures.isEmpty())
      throw new ConnectorFetchException(
          failures.getFirst().failureType(),
          failures.stream()
              .map(ConnectorFetchException::getMessage)
              .collect(java.util.stream.Collectors.joining("; ")),
          failures.getFirst());
  }

  private void detail(CtbClient client, IngestionSink sink, StocktakeInput header) {
    List<StockCountInput> lines = new ArrayList<>();
    Set<Integer> ids = new HashSet<>();
    int total = -1;
    for (int pageIndex = 0; pageIndex < MAX_PAGES; pageIndex++) {
      int start = pageIndex * PAGE_SIZE;
      var page = client.getStocktakeDetail(header.stocktakeId(), start, PAGE_SIZE);
      UUID raw = write(sink, page, "ctb-stocktake-detail", "stocktake:" + header.stocktakeId());
      var parsed =
          parser.parseStocktakeLines(
              page.json().getBytes(StandardCharsets.UTF_8),
              raw,
              header.stocktakeId(),
              header.stocktakeDate());
      checkPage(page, total, start, parsed.size());
      total = page.totalCount();
      for (var line : parsed) {
        if (!ids.add(line.lineId()))
          throw new ConnectorFetchException(
              "CONNECTOR_TRUNCATED", "Stocktake pages repeat a count identity");
        lines.add(line);
      }
      if (start + PAGE_SIZE >= total) break;
    }
    if (lines.size() != total)
      throw new ConnectorFetchException(
          "CONNECTOR_TRUNCATED", "Stocktake detail population is incomplete");
    counts.recordAll(lines);
  }

  private static void checkPage(CtbClient.CtbPage page, int total, int start, int size) {
    if (page.totalCount() < 0
        || (total >= 0 && total != page.totalCount())
        || size != Math.min(PAGE_SIZE, page.totalCount() - start))
      throw new ConnectorFetchException(
          "CONNECTOR_TRUNCATED",
          "Stocktake page count changed or differs from advertised population");
  }

  private static UUID write(
      IngestionSink sink, CtbClient.CtbPage page, String fetcher, String ref) {
    return sink.accept(
        new FetchedPayload(
            FetchMethod.API,
            "application/json",
            page.json().getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8.name(),
            fetcher,
            ref));
  }
}
