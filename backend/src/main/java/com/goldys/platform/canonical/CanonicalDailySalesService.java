package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical daily sales totals, locking the current source fact so concurrent corrections
 * cannot leave two current versions.
 *
 * <p>The logical identity is a deterministic UUID of the trading date, so the date-keyed matching
 * between sources is implicit: both Lightspeed's and CTB's rows for a day share the same logical id
 * without a separate matching step.
 */
@Service
class CanonicalDailySalesService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalDailySalesRepository repository;

  CanonicalDailySalesService(CanonicalDailySalesRepository repository) {
    this.repository = repository;
  }

  @Transactional
  CanonicalDailySales record(DailySalesInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  CanonicalDailySales recordAt(DailySalesInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.tradingDate());
    String ref = input.tradingDate().toString();
    Optional<CanonicalDailySales> current =
        repository.lockCurrentDailySale(input.tradingDate(), input.sourceSystem());

    if (current.isPresent()) {
      CanonicalDailySales existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalDailySales.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.tradingDate(),
            input.totalSales(),
            input.gstTotal(),
            input.netTotal()));
  }

  private static UUID logicalIdFor(LocalDate date) {
    return UUID.nameUUIDFromBytes(("daily-sales:" + date).getBytes(StandardCharsets.UTF_8));
  }
}
