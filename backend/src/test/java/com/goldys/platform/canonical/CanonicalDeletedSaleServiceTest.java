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

class CanonicalDeletedSaleServiceTest {

  private static final UUID RAW = UUID.randomUUID();
  private static final LocalDate DATE = LocalDate.parse("2025-07-05");

  private CanonicalDeletedSaleRepository repository;
  private ApplicationEventPublisher publisher;
  private CanonicalDeletedSaleService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalDeletedSaleRepository.class);
    publisher = mock(ApplicationEventPublisher.class);
    service = new CanonicalDeletedSaleService(repository, publisher);
  }

  private static DeletedSaleInput input(BigDecimal totalIncTax) {
    return new DeletedSaleInput(
        "LIGHTSPEED",
        DATE,
        "SP-1",
        "Dine in",
        null,
        totalIncTax,
        new BigDecimal("29.10"),
        new BigDecimal("2.90"),
        null,
        "G1",
        "Goldy's",
        "G1",
        "Goldy's",
        "Staff",
        "S1",
        "Staff",
        "S1",
        "12",
        "96181",
        null,
        RAW);
  }

  @Test
  void sameFactTreatsAmountScaleAsEquivalent() {
    when(repository.lockCurrentDeletedSale("SP-1", "LIGHTSPEED")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalDeletedSale saved =
        service.recordAt(input(new BigDecimal("32")), Instant.parse("2026-10-10T00:00:00Z"));

    when(repository.lockCurrentDeletedSale("SP-1", "LIGHTSPEED")).thenReturn(Optional.of(saved));
    CanonicalDeletedSale again =
        service.recordAt(input(new BigDecimal("32.0000")), Instant.parse("2026-10-10T01:00:00Z"));

    assertThat(again).isSameAs(saved);
    verify(repository, times(1)).save(any(CanonicalDeletedSale.class));
  }
}
