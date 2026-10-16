package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalStockCountIngest;
import com.goldys.platform.canonical.CanonicalStocktakeIngest;
import com.goldys.platform.canonical.CanonicalWastageIngest;
import com.goldys.platform.canonical.StocktakeInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off replay: canonicalizes the {@code ctb-stocktakes}, {@code ctb-stocktake-detail} and {@code
 * ctb-wastage-detail} raw records already in the ledger (from the CTB scheduled pull and the detail
 * backfill) into {@code canonical_stocktake}, {@code canonical_stock_count} and {@code
 * canonical_wastage}. Runs only when {@code app.stock-wastage-canonicalize=true}, and is idempotent
 * — canonical services track processed raw receipts and reject regressive replay.
 *
 * <p>Stocktake detail carries no date, so a line's {@code counted_date} is resolved from the
 * canonical header. A line whose stocktake has no header is a hard failure (never a silent skip),
 * because a dated stock-count fact cannot be recorded without it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.stock-wastage-canonicalize", havingValue = "true")
public class StockWastageCanonicalizationRunner implements ApplicationRunner {
  private static final Logger log =
      LoggerFactory.getLogger(StockWastageCanonicalizationRunner.class);

  private final RawLedgerQuery rawLedger;
  private final CtbStocktakeParser stocktakeParser;
  private final CtbWastageParser wastageParser;
  private final CanonicalStocktakeIngest stocktakeIngest;
  private final CanonicalStockCountIngest stockCountIngest;
  private final CanonicalWastageIngest wastageIngest;

  public StockWastageCanonicalizationRunner(
      RawLedgerQuery rawLedger,
      CtbStocktakeParser stocktakeParser,
      CtbWastageParser wastageParser,
      CanonicalStocktakeIngest stocktakeIngest,
      CanonicalStockCountIngest stockCountIngest,
      CanonicalWastageIngest wastageIngest) {
    this.rawLedger = rawLedger;
    this.stocktakeParser = stocktakeParser;
    this.wastageParser = wastageParser;
    this.stocktakeIngest = stocktakeIngest;
    this.stockCountIngest = stockCountIngest;
    this.wastageIngest = wastageIngest;
  }

  @Override
  public void run(ApplicationArguments args) {
    Map<Integer, StocktakeInput> latest = latestHeaders();
    List<RawLedgerQuery.RawRecordRef> detailRefs =
        rawLedger.rawRecordRefsForFetcher("CTB", "ctb-stocktake-detail");
    // Validate date dependencies before writing any headers/counts for this replay pass.
    validateHeaders(detailRefs, latest);
    for (StocktakeInput header : latest.values()) stocktakeIngest.record(header);

    int counts = 0;
    for (RawLedgerQuery.RawRecordRef ref : detailRefs) {
      Integer id = stocktakeId(ref.sourceRecordRef());
      if (id == null) {
        log.warn(
            "Skipping ctb-stocktake-detail record {} with unparseable source reference", ref.id());
        continue;
      }
      var rows =
          stocktakeParser.parseStocktakeLines(
              payload(ref), ref.id(), id, latest.get(id).stocktakeDate());
      // One payload/transaction, never a whole-ledger batch. Receipt and version writes are atomic.
      stockCountIngest.recordAll(rows);
      counts += rows.size();
    }
    int wastages = 0;
    for (RawLedgerQuery.RawRecordRef ref :
        rawLedger.rawRecordRefsForFetcher("CTB", "ctb-wastage-detail")) {
      var rows = wastageParser.parse(payload(ref), ref.id());
      wastageIngest.recordAll(rows);
      wastages += rows.size();
    }
    log.info(
        "Stock/wastage replay processed {} headers, {} count rows, {} wastage rows",
        latest.size(),
        counts,
        wastages);
  }

  private Map<Integer, StocktakeInput> latestHeaders() {
    Map<Integer, StocktakeInput> latest = new LinkedHashMap<>();
    // References are ordered by fetched_at then UUID, matching the canonical receipt guard.
    // Only the latest header per stocktake is selected, preserving the existing backfill behaviour.
    for (RawLedgerQuery.RawRecordRef ref :
        rawLedger.rawRecordRefsForFetcher("CTB", "ctb-stocktakes")) {
      for (StocktakeInput header : stocktakeParser.parseStocktakes(payload(ref), ref.id()))
        latest.put(header.stocktakeId(), header);
    }
    return latest;
  }

  private void validateHeaders(
      List<RawLedgerQuery.RawRecordRef> refs, Map<Integer, StocktakeInput> headers) {
    Set<Integer> missing = new LinkedHashSet<>();
    for (RawLedgerQuery.RawRecordRef ref : refs) {
      Integer id = stocktakeId(ref.sourceRecordRef());
      if (id != null && !headers.containsKey(id)) missing.add(id);
    }
    if (!missing.isEmpty())
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH",
          "stocktake detail missing header date for stocktakes: " + missing);
  }

  private byte[] payload(RawLedgerQuery.RawRecordRef ref) {
    byte[] bytes = rawLedger.payloadBytes(ref.id());
    if (bytes == null)
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Stored inventory payload is unavailable");
    return bytes;
  }

  private static Integer stocktakeId(String sourceRecordRef) {
    if (sourceRecordRef == null || !sourceRecordRef.startsWith("stocktake:")) {
      return null;
    }
    try {
      return Integer.parseInt(sourceRecordRef.substring("stocktake:".length()));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
