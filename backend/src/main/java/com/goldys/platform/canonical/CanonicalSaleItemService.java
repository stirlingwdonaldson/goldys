package com.goldys.platform.canonical;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical sale items, locking the current source fact so concurrent corrections cannot
 * leave two current versions.
 *
 * <p>An unchanged retry returns the existing row rather than appending a new version; a changed
 * fact closes the current row and inserts a successor that shares its logical identity.
 */
@Service
class CanonicalSaleItemService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalSaleItemRepository repository;

  CanonicalSaleItemService(CanonicalSaleItemRepository repository) {
    this.repository = repository;
  }

  @Transactional
  CanonicalSaleItem record(SaleItemInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  CanonicalSaleItem recordAt(SaleItemInput input, Instant recordedAt) {
    Optional<CanonicalSaleItem> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), input.sourceRecordRef());

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
              input.sourceRecordRef(),
              input.rawRecordId(),
              recordedAt,
              recordedAt,
              input.itemName(),
              input.quantitySold(),
              input.amount()));
    }

    // A new source fact starts with a provisional logical identity; the matching plan may link it
    // to an existing entity before reconciliation begins.
    return repository.save(
        CanonicalSaleItem.create(
            UUID.randomUUID(),
            input.sourceSystem(),
            input.sourceRecordRef(),
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.itemName(),
            input.quantitySold(),
            input.amount()));
  }
}
