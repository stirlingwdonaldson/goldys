package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical stock-count facts, locking the current source fact so concurrent corrections
 * cannot leave two current versions. The logical identity is a deterministic UUID of {@code
 * (stocktakeId, lineId)} — the line id is per-stocktake-scoped, so the stocktake id must anchor it.
 */
@Service
class CanonicalStockCountService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalStockCountRepository repository;

  CanonicalStockCountService(CanonicalStockCountRepository repository) {
    this.repository = repository;
  }

  @Transactional
  CanonicalStockCount record(StockCountInput input) {
    return recordAt(input, CLOCK.instant());
  }

  /** Record a batch of facts in one transaction. No event: no projector listens this slice. */
  @Transactional
  void recordAll(List<StockCountInput> inputs) {
    for (StockCountInput input : inputs) {
      recordAt(input, CLOCK.instant());
    }
  }

  @Transactional
  CanonicalStockCount recordAt(StockCountInput input, Instant recordedAt) {
    String ref = sourceRef(input.stocktakeId(), input.lineId());
    UUID logicalId = logicalIdFor(input.stocktakeId(), input.lineId());
    Optional<CanonicalStockCount> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), ref);

    if (current.isPresent()) {
      CanonicalStockCount existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalStockCount.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.stocktakeId(),
            input.lineId(),
            input.countedDate(),
            input.productNameKey(),
            input.quantityOnHand(),
            input.stockId(),
            input.stockCode(),
            input.unitOfMeasurementName(),
            input.unitCost(),
            input.lineValue(),
            input.supplierName(),
            input.areaName(),
            input.recipeId()));
  }

  private static String sourceRef(int stocktakeId, int lineId) {
    return "stocktake:" + stocktakeId + ":" + lineId;
  }

  private static UUID logicalIdFor(int stocktakeId, int lineId) {
    return UUID.nameUUIDFromBytes(
        ("stock-count:" + stocktakeId + ":" + lineId).getBytes(StandardCharsets.UTF_8));
  }
}
