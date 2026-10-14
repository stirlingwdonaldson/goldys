package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalStockCountIngest;
import com.goldys.platform.canonical.CanonicalStocktakeIngest;
import com.goldys.platform.canonical.CanonicalWastageIngest;
import com.goldys.platform.canonical.StockCountInput;
import com.goldys.platform.canonical.StocktakeInput;
import com.goldys.platform.canonical.WastageInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
 * — the canonical services dedupe on {@code source_record_ref}.
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
    // 1. Latest header per stocktake id. Records arrive in ascending fetched_at order, so the last
    // one seen for an id is the most recent (headers are re-pulled daily, append-only).
    Map<Integer, StocktakeInput> latest = new HashMap<>();
    for (RawLedgerQuery.RawPayloadWithRefAndTime payload :
        rawLedger.rawPayloadsForFetcherWithFetchedAt("CTB", "ctb-stocktakes")) {
      for (StocktakeInput header : stocktakeParser.parseStocktakes(payload.bytes(), payload.id())) {
        latest.put(header.stocktakeId(), header);
      }
    }

    // 2. Record headers and build the stocktake-id → date map the lines need.
    Map<Integer, LocalDate> dates = new HashMap<>();
    for (StocktakeInput header : latest.values()) {
      stocktakeIngest.record(header);
      dates.put(header.stocktakeId(), header.stocktakeDate());
    }

    // 3. Stock-count lines, resolving each line's date from the header map.
    List<StockCountInput> counts = new ArrayList<>();
    Set<Integer> missing = new LinkedHashSet<>();
    for (RawLedgerQuery.RawPayloadWithRef payload :
        rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-stocktake-detail")) {
      Integer stocktakeId = stocktakeId(payload.sourceRecordRef());
      if (stocktakeId == null) {
        log.warn(
            "Skipping ctb-stocktake-detail record {} with unparseable source_record_ref {}",
            payload.id(),
            payload.sourceRecordRef());
        continue;
      }
      LocalDate countedDate = dates.get(stocktakeId);
      if (countedDate == null) {
        missing.add(stocktakeId);
        continue;
      }
      counts.addAll(
          stocktakeParser.parseStocktakeLines(
              payload.bytes(), payload.id(), stocktakeId, countedDate));
    }

    // 4. Wastage lines (self-contained: each line carries its own record date).
    List<WastageInput> wastages = new ArrayList<>();
    for (RawLedgerQuery.RawPayloadWithRef payload :
        rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-wastage-detail")) {
      wastages.addAll(wastageParser.parse(payload.bytes(), payload.id()));
    }

    // 5. Fail fast before any count write when a detail's header date is missing.
    if (!missing.isEmpty()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH",
          "stocktake detail missing header date for stocktakes: " + missing);
    }

    stockCountIngest.recordAll(counts);
    wastageIngest.recordAll(wastages);
    log.info(
        "Stock/wastage canonicalization recorded {} headers, {} counts, {} wastage lines",
        latest.size(),
        counts.size(),
        wastages.size());
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
