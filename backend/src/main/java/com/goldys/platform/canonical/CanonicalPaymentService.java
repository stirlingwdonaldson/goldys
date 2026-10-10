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
 * Records canonical payment facts, locking the current source fact so concurrent corrections cannot
 * leave two current versions.
 *
 * <p>Lightspeed exposes no payment id, so the source record reference is a composite of the fields
 * that jointly identify a tender: {@code sale_number|payment_type_code|register_code|trading_date}.
 * This is unique in the captured data and stable across re-deliveries (see the design spec for the
 * open question).
 */
@Service
class CanonicalPaymentService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalPaymentRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalPaymentService(
      CanonicalPaymentRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.publisher = publisher;
  }

  @Transactional
  CanonicalPayment record(PaymentInput input) {
    CanonicalPayment saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new PaymentRecorded(input.tradingDate()));
    return saved;
  }

  @Transactional
  CanonicalPayment recordAt(PaymentInput input, Instant recordedAt) {
    String ref = referenceFor(input);
    UUID logicalId = logicalIdFor(ref);
    Optional<CanonicalPayment> current = repository.lockCurrentPayment(ref, input.sourceSystem());

    if (current.isPresent()) {
      CanonicalPayment existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalPayment.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input));
  }

  private static String referenceFor(PaymentInput input) {
    return String.join(
        "|",
        input.saleNumber(),
        nz(input.paymentTypeCode()),
        nz(input.registerCode()),
        input.tradingDate().toString());
  }

  private static UUID logicalIdFor(String ref) {
    return UUID.nameUUIDFromBytes(("payment:" + ref).getBytes(StandardCharsets.UTF_8));
  }

  private static String nz(String v) {
    return v == null ? "" : v;
  }
}
