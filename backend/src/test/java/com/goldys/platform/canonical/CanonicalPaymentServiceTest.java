package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CanonicalPaymentServiceTest {

  private static final UUID RAW = UUID.randomUUID();
  private static final LocalDate DATE = LocalDate.parse("2025-07-05");
  private static final String REF = "SP-1|Tyro|BAR_2|2025-07-05";

  private CanonicalPaymentRepository repository;
  private ApplicationEventPublisher publisher;
  private CanonicalPaymentService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalPaymentRepository.class);
    publisher = mock(ApplicationEventPublisher.class);
    service = new CanonicalPaymentService(repository, publisher);
  }

  private static PaymentInput input(BigDecimal amount) {
    return new PaymentInput(
        "LIGHTSPEED",
        DATE,
        "SP-1",
        "Tyro",
        "Tyro",
        "4",
        null,
        null,
        amount,
        BigDecimal.ZERO,
        amount,
        BigDecimal.ZERO,
        1,
        0,
        "Reconciled",
        "BAR_2",
        "BAR.2",
        "Staff",
        null,
        "96181",
        null,
        RAW);
  }

  @Test
  void sameFactTreatsAmountScaleAsEquivalent() {
    when(repository.lockCurrentPayment(REF, "LIGHTSPEED")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalPayment saved =
        service.recordAt(input(new BigDecimal("222")), Instant.parse("2026-10-10T00:00:00Z"));

    when(repository.lockCurrentPayment(REF, "LIGHTSPEED")).thenReturn(Optional.of(saved));
    CanonicalPayment again =
        service.recordAt(input(new BigDecimal("222.0000")), Instant.parse("2026-10-10T01:00:00Z"));

    assertThat(again).isSameAs(saved);
    verify(repository, times(1)).save(any(CanonicalPayment.class));
  }
}
