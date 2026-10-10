package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical sale line items, locking the current source fact so concurrent corrections
 * cannot leave two current versions. The logical identity is the deterministic UUID of the receipt
 * line id, which Lightspeed exposes and is stable across report re-deliveries.
 */
@Service
class CanonicalSaleItemService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalSaleItemRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalSaleItemService(
      CanonicalSaleItemRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.publisher = publisher;
  }

  @Transactional
  CanonicalSaleItem record(SaleItemInput input) {
    CanonicalSaleItem saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new SaleItemRecorded(input.tradingDate()));
    return saved;
  }

  @Transactional
  CanonicalSaleItem recordAt(SaleItemInput input, Instant recordedAt) {
    String ref = input.receiptLineId();
    UUID logicalId = UUID.nameUUIDFromBytes(("sale-item:" + ref).getBytes(StandardCharsets.UTF_8));
    Optional<CanonicalSaleItem> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), ref);

    if (current.isPresent()) {
      CanonicalSaleItem existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      // Flush the supersession UPDATE before inserting the successor: Hibernate's default flush
      // order runs INSERTs first, which would otherwise violate the one-current-version index.
      repository.saveAndFlush(existing);
      return repository.save(
          CanonicalSaleItem.create(
              existing.logicalEntityId(),
              input.sourceSystem(),
              ref,
              input.rawRecordId(),
              recordedAt,
              recordedAt,
              input));
    }

    return repository.save(
        CanonicalSaleItem.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input));
  }
}
