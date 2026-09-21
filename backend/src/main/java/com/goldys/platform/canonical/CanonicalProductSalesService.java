package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records canonical per-product daily totals; idempotent and name-keyed. */
@Service
class CanonicalProductSalesService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalProductSalesRepository repository;

  CanonicalProductSalesService(CanonicalProductSalesRepository repository) {
    this.repository = repository;
  }

  @Transactional
  CanonicalProductSales record(ProductSalesInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  CanonicalProductSales recordAt(ProductSalesInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.productNameKey(), input.tradingDate());
    String ref = input.productNameKey() + "@" + input.tradingDate();
    Optional<CanonicalProductSales> current =
        repository.lockCurrent(input.productNameKey(), input.tradingDate(), input.sourceSystem());
    if (current.isPresent()) {
      CanonicalProductSales existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }
    return repository.save(
        CanonicalProductSales.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.tradingDate(),
            input.productNameKey(),
            input.quantitySold(),
            input.amount()));
  }

  private static UUID logicalIdFor(String key, java.time.LocalDate date) {
    return UUID.nameUUIDFromBytes(
        ("product-sales:" + key + ":" + date).getBytes(StandardCharsets.UTF_8));
  }
}
