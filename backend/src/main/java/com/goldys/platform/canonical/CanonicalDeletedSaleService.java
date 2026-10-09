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
 * Records canonical deleted-order facts, locking the current source fact so concurrent corrections
 * cannot leave two current versions. The sale number is the natural key.
 */
@Service
class CanonicalDeletedSaleService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalDeletedSaleRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalDeletedSaleService(
      CanonicalDeletedSaleRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.publisher = publisher;
  }

  @Transactional
  CanonicalDeletedSale record(DeletedSaleInput input) {
    CanonicalDeletedSale saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new DeletedSaleRecorded(input.tradingDate()));
    return saved;
  }

  @Transactional
  CanonicalDeletedSale recordAt(DeletedSaleInput input, Instant recordedAt) {
    String ref = input.saleNumber();
    UUID logicalId =
        UUID.nameUUIDFromBytes(("deleted-sale:" + ref).getBytes(StandardCharsets.UTF_8));
    Optional<CanonicalDeletedSale> current =
        repository.lockCurrentDeletedSale(ref, input.sourceSystem());

    if (current.isPresent()) {
      CanonicalDeletedSale existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalDeletedSale.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input));
  }
}
