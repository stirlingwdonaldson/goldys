package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.SourceProductSaleFactIngest;
import com.goldys.platform.canonical.SourceProductSaleFactInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off replay: recovers CTB source-native product-sale measures (cost, food cost, discount, tax,
 * unit price, profit) from retained {@code ctb-revenue} raw records into {@code
 * source_product_sale_fact}. Runs only when {@code app.source-product-sale-fact-backfill=true},
 * before the projection seeder, and is idempotent — the canonical service dedupes on {@code
 * source_record_ref}. Records are processed in {@code fetched_at} order so a later re-pull of the
 * same product-day supersedes an earlier one.
 *
 * <p>A sale-item page's trading date is the request window, not a payload field (CTB's {@code
 * saleDate} is null on every retained row). It is recovered in two durably-provenanced ways, never
 * guessed from {@code fetched_at} or ordering: (1) the {@code sale-items:<day>} source-record
 * reference the connector now writes on the raw record, and (2) for older records written without
 * that reference, the {@code canonical_product_sales.raw_record_id → trading_date} mapping the
 * connector always recorded. A sale-item-shaped record with no recoverable date is
 * <em>ambiguous</em> and left unrecovered; a blank-stock-code row or an unparseable day/payload is
 * <em>rejected</em>; a revenue page is <em>skipped</em>.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.source-product-sale-fact-backfill", havingValue = "true")
public class SourceProductSaleFactBackfillRunner implements ApplicationRunner {
  private static final Logger log =
      LoggerFactory.getLogger(SourceProductSaleFactBackfillRunner.class);
  private static final String SALE_ITEMS_REF_PREFIX = "sale-items:";

  private final RawLedgerQuery rawLedger;
  private final CtbSaleItemParser parser;
  private final SourceProductSaleFactIngest ingest;
  private final CanonicalProductSalesQuery productSales;

  public SourceProductSaleFactBackfillRunner(
      RawLedgerQuery rawLedger,
      CtbSaleItemParser parser,
      SourceProductSaleFactIngest ingest,
      CanonicalProductSalesQuery productSales) {
    this.rawLedger = rawLedger;
    this.parser = parser;
    this.ingest = ingest;
    this.productSales = productSales;
  }

  @Override
  public void run(ApplicationArguments args) {
    Result result = replay();
    log.info(
        "source_product_sale_fact backfill processed={} accepted={} rejected={} ambiguous={} skipped={}",
        result.processed(),
        result.accepted(),
        result.rejected(),
        result.ambiguous(),
        result.skipped());
  }

  /** One replay pass over retained {@code ctb-revenue} records, returning a count summary. */
  public Result replay() {
    int processed = 0;
    int accepted = 0;
    int rejected = 0;
    int ambiguous = 0;
    int skipped = 0;

    for (RawLedgerQuery.RawRecordRef ref :
        rawLedger.rawRecordRefsForFetcher("CTB", "ctb-revenue")) {
      processed++;
      LocalDate day = null;
      if (ref.sourceRecordRef() != null
          && ref.sourceRecordRef().startsWith(SALE_ITEMS_REF_PREFIX)) {
        day = parseDay(ref.sourceRecordRef());
        if (day == null) {
          rejected++;
          continue;
        }
      } else if (ref.sourceRecordRef() != null) {
        // A non-sale-items reference on the ctb-revenue fetcher is unexpected; not product sales.
        skipped++;
        continue;
      }

      byte[] bytes = rawLedger.payloadBytes(ref.id());
      if (bytes == null) {
        skipped++;
        continue;
      }

      if (day == null) {
        // No source-record reference: an older record. Recover its date from the durable canonical
        // mapping, or treat it as ambiguous rather than guess.
        if (!parser.isSaleItemEnvelope(bytes)) {
          skipped++;
          continue;
        }
        List<LocalDate> dates = productSales.tradingDatesForRawRecord(ref.id());
        if (dates.size() == 1) {
          day = dates.get(0);
        } else {
          ambiguous++;
          continue;
        }
      }

      List<CtbSaleItem> items;
      try {
        items = parser.parse(bytes);
      } catch (ConnectorFetchException e) {
        log.warn("Skipping unparseable sale-items record {}: {}", ref.id(), e.getMessage());
        rejected++;
        continue;
      }
      List<SourceProductSaleFactInput> toRecord = new ArrayList<>();
      for (CtbSaleItem item : items) {
        String code = item.stockCode();
        if (code == null || code.isBlank()) {
          rejected++;
          continue;
        }
        accepted++;
        toRecord.add(
            new SourceProductSaleFactInput(
                "CTB",
                SourceProductSaleFactInput.sourceRecordRef(code, day, item.uniqueGroupId()),
                day,
                code,
                item.costPerUnit(),
                item.foodCost(),
                item.totalFoodCost(),
                item.profit(),
                item.discountedAmount(),
                item.taxAmount(),
                item.unitPrice(),
                item.uniqueGroupId(),
                ref.id(),
                item.quantitySold(),
                item.amount(),
                item.stockDescription(),
                item.siteNumber()));
      }
      if (!toRecord.isEmpty()) ingest.recordAll(toRecord);
    }
    return new Result(processed, accepted, rejected, ambiguous, skipped);
  }

  private static LocalDate parseDay(String ref) {
    try {
      return LocalDate.parse(ref.substring(SALE_ITEMS_REF_PREFIX.length()));
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /**
   * Count summary of one replay pass. {@code processed} is raw records examined; {@code accepted}
   * is source facts handed to the ingest; {@code rejected} is records/rows refused (unparseable day
   * or payload, blank stock code); {@code ambiguous} is sale-item records with no recoverable
   * trading date; {@code skipped} is non-sale-item records (revenue pages).
   */
  public record Result(int processed, int accepted, int rejected, int ambiguous, int skipped) {}
}
