package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalStocktakeServiceTest {

  private static final UUID RAW = UUID.randomUUID();

  private CanonicalStocktakeRepository repository;
  private CanonicalStocktakeService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalStocktakeRepository.class);
    InventoryObservationReceiptRepository receipts =
        mock(InventoryObservationReceiptRepository.class);
    when(receipts.accept(
            anyString(),
            anyString(),
            anyString(),
            any(),
            org.mockito.ArgumentMatchers.nullable(UUID.class)))
        .thenReturn(true);
    service = new CanonicalStocktakeService(repository, receipts);
  }

  private static StocktakeInput input(LocalDate date, Boolean isLocked) {
    return new StocktakeInput(
        "CTB",
        1,
        date,
        "Weekly stocktake",
        "Goldy's",
        new BigDecimal("1234.50"),
        false,
        isLocked,
        RAW);
  }

  @Test
  void recordsNewFactAndSetsFields() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalStocktake saved = service.record(input(LocalDate.parse("2026-10-05"), true));

    assertThat(saved.stocktakeId()).isEqualTo(1);
    assertThat(saved.stocktakeDate()).isEqualTo(LocalDate.parse("2026-10-05"));
    assertThat(saved.isLocked()).isTrue();
  }

  @Test
  void supersedesOnChangedFact() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalStocktake saved =
        service.recordAt(
            input(LocalDate.parse("2026-10-05"), true), Instant.parse("2026-10-12T00:00:00Z"));

    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.of(saved));
    service.recordAt(
        input(LocalDate.parse("2026-10-05"), false), Instant.parse("2026-10-12T01:00:00Z"));

    assertThat(saved.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(saved);
    verify(repository, times(2)).save(any(CanonicalStocktake.class));
  }

  @Test
  void sameFactIsANoop() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalStocktake saved = service.record(input(LocalDate.parse("2026-10-05"), true));

    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.of(saved));
    CanonicalStocktake again = service.record(input(LocalDate.parse("2026-10-05"), true));

    assertThat(again).isSameAs(saved);
    verify(repository, times(1)).save(any(CanonicalStocktake.class));
  }

  @Test
  void logicalIdIsDeterministic() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalStocktake saved = service.record(input(LocalDate.parse("2026-10-05"), true));

    assertThat(saved.logicalEntityId())
        .isEqualTo(UUID.nameUUIDFromBytes("stocktake:1".getBytes(StandardCharsets.UTF_8)));
  }
}
