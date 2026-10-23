package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.canonical.CanonicalSaleItemQuery;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.IngestionStageKind;
import com.goldys.platform.ingestion.IngestionStageOutcome;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Explicit, insert-only recovery of one reviewed raw report. Preview is the default. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.lightspeed-sale-items-recovery.enabled", havingValue = "true")
public class LightspeedSaleItemRecoveryRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(LightspeedSaleItemRecoveryRunner.class);
  private final RawLedgerQuery ledger;
  private final LightspeedSaleItemIngestService ingest;
  private final CanonicalSaleItemQuery canonical;
  private final IngestionService ingestion;
  private final UUID rawId;
  private final boolean dryRun;
  private final long expectedKeys;
  private final long expectedConflicts;

  public LightspeedSaleItemRecoveryRunner(
      RawLedgerQuery ledger,
      LightspeedSaleItemIngestService ingest,
      CanonicalSaleItemQuery canonical,
      IngestionService ingestion,
      @Value("${app.lightspeed-sale-items-recovery.raw-id}") UUID rawId,
      @Value("${app.lightspeed-sale-items-recovery.dry-run:true}") boolean dryRun,
      @Value("${app.lightspeed-sale-items-recovery.expected-keys:-1}") long expectedKeys,
      @Value("${app.lightspeed-sale-items-recovery.expected-conflicts:0}") long expectedConflicts) {
    this.ledger = ledger;
    this.ingest = ingest;
    this.canonical = canonical;
    this.ingestion = ingestion;
    this.rawId = rawId;
    this.dryRun = dryRun;
    this.expectedKeys = expectedKeys;
    this.expectedConflicts = expectedConflicts;
  }

  @Override
  public void run(ApplicationArguments args) {
    Result result = recover();
    log.info(
        "Sale-item recovery raw={} preview={} rows={} keys={} missingBefore={} inserted={} missingAfter={} conflictingKeys={} heldMissing={}",
        rawId,
        dryRun,
        result.rows(),
        result.keys(),
        result.missingBefore(),
        result.inserted(),
        result.missingAfter(),
        result.conflictingKeys(),
        result.heldMissing());
  }

  public Result recover() {
    if (!dryRun && expectedKeys < 1)
      throw new IllegalArgumentException("Recovery requires an expected distinct-key count");
    boolean belongs =
        ledger.rawRecordRefsForFetcher("LIGHTSPEED", "lightspeed-sale-items").stream()
            .anyMatch(ref -> ref.id().equals(rawId));
    if (!belongs)
      throw new IllegalArgumentException("Raw record is not a Lightspeed sale-item report");
    byte[] bytes = ledger.payloadBytes(rawId);
    if (bytes == null) throw new IllegalStateException("Recovery payload is missing");
    var payload = new RawLedgerQuery.RawPayload(rawId, bytes);
    List<LightspeedSaleItem> rows;
    try {
      rows = ingest.preview(payload);
    } catch (RuntimeException e) {
      if (!dryRun)
        ingestion.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
      throw e;
    }
    Map<String, LightspeedSaleItem> unique = new LinkedHashMap<>();
    var conflicts = new java.util.HashSet<String>();
    for (LightspeedSaleItem row : rows) {
      LightspeedSaleItem prior = unique.putIfAbsent(row.receiptLineId(), row);
      if (prior != null && !prior.equals(row)) conflicts.add(row.receiptLineId());
    }
    if (expectedKeys >= 0 && unique.size() != expectedKeys)
      throw new IllegalStateException("Distinct-key count differs from reviewed recovery manifest");
    int keys = unique.size();
    var current = canonical.currentSourceRefs("LIGHTSPEED", unique.keySet());
    int heldMissing = (int) conflicts.stream().filter(key -> !current.contains(key)).count();
    conflicts.forEach(unique::remove); // Ambiguous source facts remain in raw for review.
    int missing = (int) unique.keySet().stream().filter(key -> !current.contains(key)).count();
    if (dryRun)
      return new Result(rows.size(), keys, missing, 0, missing, conflicts.size(), heldMissing);
    if (conflicts.size() != expectedConflicts)
      throw new IllegalStateException(
          "Conflicting-key count differs from reviewed recovery manifest");
    ingestion.recordStage(
        rawId,
        IngestionStageKind.PARSED,
        rows.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
    int inserted = ingest.recoverMissing(payload, List.copyOf(unique.values()));
    int remaining =
        unique.size() - canonical.currentSourceRefs("LIGHTSPEED", unique.keySet()).size();
    if (remaining != 0) {
      ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw new IllegalStateException("Recovery finished with missing canonical keys");
    }
    return new Result(
        rows.size(), keys, missing, inserted, remaining, conflicts.size(), heldMissing);
  }

  public record Result(
      int rows,
      int keys,
      int missingBefore,
      int inserted,
      int missingAfter,
      int conflictingKeys,
      int heldMissing) {}
}
