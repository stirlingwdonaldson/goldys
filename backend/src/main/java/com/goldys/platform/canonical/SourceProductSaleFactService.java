package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records source-native product-day measures, closing the prior version on change. */
@Service
class SourceProductSaleFactService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final SourceProductSaleFactRepository repository;

  SourceProductSaleFactService(SourceProductSaleFactRepository repository) {
    this.repository = repository;
  }

  @Transactional
  void record(SourceProductSaleFactInput input) {
    recordAt(input, CLOCK.instant());
  }

  /** Batch record for replay: one transaction, each input deduped/superseded on its source ref. */
  @Transactional
  void recordAll(List<SourceProductSaleFactInput> inputs) {
    for (SourceProductSaleFactInput input : inputs) {
      recordAt(input, CLOCK.instant());
    }
  }

  @Transactional
  void recordAt(SourceProductSaleFactInput input, Instant recordedAt) {
    String sourceRef = input.sourceRecordRef();
    UUID logicalId =
        UUID.nameUUIDFromBytes(
            ("source-product-sale-fact:" + input.sourceSystem() + ":" + sourceRef)
                .getBytes(StandardCharsets.UTF_8));
    Optional<SourceProductSaleFact> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), sourceRef);

    if (current.isPresent()
        && current.get().siteNumber() != null
        && input.siteNumber() != null
        && !current.get().siteNumber().equals(input.siteNumber()))
      throw new IllegalArgumentException(
          "Site-scoped source observation collision requires review");
    // A receipt is registered even when its values equal the current fact. Otherwise replaying
    // an identical intermediate receipt after a correction would manufacture an old revision.
    if (repository.registerObservation(input.sourceSystem(), sourceRef, input.rawRecordId()) == 0
        || repository.hasLaterObservation(input.sourceSystem(), sourceRef, input.rawRecordId()))
      return;

    if (current.isPresent()) {
      SourceProductSaleFact existing = current.get();
      if (existing.sameFact(input)) {
        return;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    SourceProductSaleFact row =
        SourceProductSaleFact.create(
            logicalId,
            input.sourceSystem(),
            sourceRef,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.tradingDate(),
            input.stockCode(),
            input.costPerUnit(),
            input.foodCost(),
            input.totalFoodCost(),
            input.profit(),
            input.discountedAmount(),
            input.taxAmount(),
            input.unitPrice(),
            input.uniqueGroupId());
    row.captureSourceFields(input);
    repository.save(row);
  }
}
