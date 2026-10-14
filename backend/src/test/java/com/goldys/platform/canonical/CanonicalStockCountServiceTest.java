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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalStockCountServiceTest {

  private static final UUID RAW = UUID.randomUUID();

  private CanonicalStockCountRepository repository;
  private CanonicalStockCountService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalStockCountRepository.class);
    service = new CanonicalStockCountService(repository);
  }

  private static StockCountInput input(int stocktakeId, int lineId, String quantity) {
    return new StockCountInput(
        "CTB",
        stocktakeId,
        lineId,
        LocalDate.parse("2026-10-05"),
        "chicken breast",
        new BigDecimal(quantity),
        42,
        "CHICKEN-1",
        "each",
        new BigDecimal("2.50"),
        new BigDecimal("12.50"),
        "Acme",
        "Cool Room",
        105,
        RAW);
  }

  @Test
  void recordsNewFactAndSetsFields() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalStockCount saved = service.record(input(1, 100, "5"));

    assertThat(saved.stocktakeId()).isEqualTo(1);
    assertThat(saved.lineId()).isEqualTo(100);
    assertThat(saved.quantityOnHand()).isEqualByComparingTo("5");
    assertThat(saved.countedDate()).isEqualTo(LocalDate.parse("2026-10-05"));
  }

  @Test
  void supersedesOnChangedFact() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalStockCount saved =
        service.recordAt(input(1, 100, "5"), Instant.parse("2026-10-12T00:00:00Z"));

    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.of(saved));
    service.recordAt(input(1, 100, "6"), Instant.parse("2026-10-12T01:00:00Z"));

    assertThat(saved.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(saved);
    verify(repository, times(2)).save(any(CanonicalStockCount.class));
  }

  @Test
  void sameFactIsANoop() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalStockCount saved = service.record(input(1, 100, "5"));

    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.of(saved));
    CanonicalStockCount again = service.record(input(1, 100, "5"));

    assertThat(again).isSameAs(saved);
    verify(repository, times(1)).save(any(CanonicalStockCount.class));
  }

  @Test
  void logicalIdIsDeterministic() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalStockCount saved = service.record(input(1, 100, "5"));

    assertThat(saved.logicalEntityId())
        .isEqualTo(UUID.nameUUIDFromBytes("stock-count:1:100".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void lineIdentityIsScopedToStocktake() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalStockCount first = service.record(input(1, 100, "5"));
    CanonicalStockCount second = service.record(input(2, 100, "5"));

    assertThat(first.sourceRecordRef()).isEqualTo("stocktake:1:100");
    assertThat(second.sourceRecordRef()).isEqualTo("stocktake:2:100");
    assertThat(first.logicalEntityId()).isNotEqualTo(second.logicalEntityId());
  }

  @Test
  void recordAllRecordsAllFacts() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.recordAll(List.of(input(1, 100, "5"), input(1, 101, "7")));

    verify(repository, times(2)).save(any(CanonicalStockCount.class));
  }
}
