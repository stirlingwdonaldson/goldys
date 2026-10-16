package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a supplier-scoped stock item. The identity is the supplier plus the stock code, so the
 * same stock code from two suppliers stays distinct. A stock item is a purchased ingredient, not a
 * sold product; the {@code sourceProductId} link is only set when source evidence connects them.
 */
@Service
class StockItemService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final StockItemRepository repository;

  StockItemService(StockItemRepository repository) {
    this.repository = repository;
  }

  @Transactional
  void record(StockItemInput input) {
    recordAt(input, CLOCK.instant());
  }

  @Transactional
  void recordAt(StockItemInput input, Instant recordedAt) {
    String sourceRef = input.sourceRecordRef();
    UUID logicalId =
        UUID.nameUUIDFromBytes(
            ("stock-item:" + input.sourceSystem() + ":" + sourceRef)
                .getBytes(StandardCharsets.UTF_8));
    Optional<StockItem> current = repository.lockCurrentSourceFact(input.sourceSystem(), sourceRef);

    if (current.isPresent()) {
      StockItem existing = current.get();
      if (existing.sameFact(input)) {
        return;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    repository.save(
        StockItem.create(
            logicalId,
            input.sourceSystem(),
            sourceRef,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.supplierLogicalId(),
            input.stockCode(),
            input.name(),
            input.sourceProductId()));
  }
}
