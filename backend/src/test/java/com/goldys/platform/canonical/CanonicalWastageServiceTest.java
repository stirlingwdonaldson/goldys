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

class CanonicalWastageServiceTest {

  private static final UUID RAW = UUID.randomUUID();

  private CanonicalWastageRepository repository;
  private CanonicalWastageService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalWastageRepository.class);
    service = new CanonicalWastageService(repository);
  }

  private static WastageInput input(int recordId, int lineId, String cost) {
    return new WastageInput(
        "CTB",
        recordId,
        lineId,
        LocalDate.parse("2026-10-05"),
        "chicken breast",
        new BigDecimal("3"),
        new BigDecimal(cost),
        "Spoilage",
        "Acme",
        "kg",
        "1kg",
        105,
        RAW);
  }

  @Test
  void recordsNewFactAndSetsFields() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalWastage saved = service.record(input(7, 359, "12"));

    assertThat(saved.wastageRecordId()).isEqualTo(7);
    assertThat(saved.lineId()).isEqualTo(359);
    assertThat(saved.cost()).isEqualByComparingTo("12");
    assertThat(saved.wastageDate()).isEqualTo(LocalDate.parse("2026-10-05"));
  }

  @Test
  void supersedesOnChangedFact() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalWastage saved =
        service.recordAt(input(7, 359, "12"), Instant.parse("2026-10-12T00:00:00Z"));

    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.of(saved));
    service.recordAt(input(7, 359, "13"), Instant.parse("2026-10-12T01:00:00Z"));

    assertThat(saved.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(saved);
    verify(repository, times(2)).save(any(CanonicalWastage.class));
  }

  @Test
  void sameFactIsANoop() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalWastage saved = service.record(input(7, 359, "12"));

    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.of(saved));
    CanonicalWastage again = service.record(input(7, 359, "12"));

    assertThat(again).isSameAs(saved);
    verify(repository, times(1)).save(any(CanonicalWastage.class));
  }

  @Test
  void logicalIdIsDeterministic() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalWastage saved = service.record(input(7, 359, "12"));

    assertThat(saved.logicalEntityId())
        .isEqualTo(UUID.nameUUIDFromBytes("wastage:7:359".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void recordAllRecordsAllFacts() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.recordAll(List.of(input(7, 359, "12"), input(7, 360, "4")));

    verify(repository, times(2)).save(any(CanonicalWastage.class));
  }
}
