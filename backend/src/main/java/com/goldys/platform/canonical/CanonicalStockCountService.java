package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
  private final CanonicalStocktakeRepository headers;
  private final InventoryObservationReceiptRepository receipts;

  CanonicalStockCountService(
      CanonicalStockCountRepository repository,
      CanonicalStocktakeRepository headers,
      InventoryObservationReceiptRepository receipts) {
    this.repository = repository;
    this.headers = headers;
    this.receipts = receipts;
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
    // Lock the date dependency first. Details have no date: a caller's old header snapshot must
    // not move a count back after a header correction. Headerless legacy inputs remain qualified.
    Optional<CanonicalStocktake> header =
        headers.lockCurrentSourceFact(input.sourceSystem(), "stocktake:" + input.stocktakeId());
    UUID headerVersion = header.map(CanonicalStocktake::id).orElse(null);
    LocalDate effectiveDate =
        header.map(CanonicalStocktake::stocktakeDate).orElse(input.countedDate());
    String ref = sourceRef(input.stocktakeId(), input.lineId());
    UUID logicalId = logicalIdFor(input.stocktakeId(), input.lineId());
    Optional<CanonicalStockCount> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), ref);

    if (!receipts.accept("COUNT", input.sourceSystem(), ref, input.rawRecordId(), headerVersion))
      return current.orElseThrow(
          () -> new IllegalStateException("Processed inventory receipt has no current fact"));

    if (current.isPresent()) {
      CanonicalStockCount existing = current.get();
      if (existing.sameFact(input, effectiveDate)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    CanonicalStockCount row =
        CanonicalStockCount.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.stocktakeId(),
            input.lineId(),
            effectiveDate,
            input.productNameKey(),
            input.quantityOnHand(),
            input.stockId(),
            input.stockCode(),
            input.unitOfMeasurementName(),
            input.unitCost(),
            input.lineValue(),
            input.supplierName(),
            input.areaName(),
            input.recipeId());
    row.captureHeaderVersion(headerVersion);
    return repository.save(row);
  }

  private static String sourceRef(int stocktakeId, int lineId) {
    return "stocktake:" + stocktakeId + ":" + lineId;
  }

  private static UUID logicalIdFor(int stocktakeId, int lineId) {
    return UUID.nameUUIDFromBytes(
        ("stock-count:" + stocktakeId + ":" + lineId).getBytes(StandardCharsets.UTF_8));
  }
}
