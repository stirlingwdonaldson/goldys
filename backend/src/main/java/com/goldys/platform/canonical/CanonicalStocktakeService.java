package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical stocktake-header facts, locking the current source fact so concurrent
 * corrections cannot leave two current versions. The logical identity is a deterministic UUID of
 * the stocktake id.
 */
@Service
class CanonicalStocktakeService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalStocktakeRepository repository;
  private final InventoryObservationReceiptRepository receipts;

  CanonicalStocktakeService(
      CanonicalStocktakeRepository repository, InventoryObservationReceiptRepository receipts) {
    this.repository = repository;
    this.receipts = receipts;
  }

  @Transactional
  CanonicalStocktake record(StocktakeInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  CanonicalStocktake recordAt(StocktakeInput input, Instant recordedAt) {
    String ref = "stocktake:" + input.stocktakeId();
    UUID logicalId = UUID.nameUUIDFromBytes(ref.getBytes(StandardCharsets.UTF_8));
    Optional<CanonicalStocktake> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), ref);

    if (!receipts.accept("HEADER", input.sourceSystem(), ref, input.rawRecordId(), null))
      return current.orElseThrow(
          () -> new IllegalStateException("Processed inventory receipt has no current fact"));

    if (current.isPresent()) {
      CanonicalStocktake existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalStocktake.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.stocktakeId(),
            input.stocktakeDate(),
            input.description(),
            input.outletName(),
            input.totalStockValue(),
            input.isPartial(),
            input.isLocked()));
  }
}
