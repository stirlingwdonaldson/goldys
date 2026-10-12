package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CanonicalRecipeCostServiceTest {

  private static final UUID RAW = UUID.randomUUID();

  private CanonicalRecipeCostRepository repository;
  private ApplicationEventPublisher publisher;
  private CanonicalRecipeCostService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalRecipeCostRepository.class);
    publisher = mock(ApplicationEventPublisher.class);
    service = new CanonicalRecipeCostService(repository, publisher);
  }

  private static RecipeCostInput input(BigDecimal costPerPortion) {
    return new RecipeCostInput(
        "CTB",
        1,
        "Parma",
        costPerPortion,
        new BigDecimal("6.50"),
        new BigDecimal("13.00"),
        "4",
        new BigDecimal("4.0000"),
        RAW);
  }

  @Test
  void recordsNewFactAndPublishesEvent() {
    when(repository.lockCurrentRecipeCost("recipe-cost:1", "CTB")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalRecipeCost saved = service.record(input(new BigDecimal("6.00")));

    assertThat(saved.recipeId()).isEqualTo(1);
    assertThat(saved.costPerPortion()).isEqualByComparingTo("6.00");
    verify(publisher).publishEvent(new RecipeCostRecorded(1));
  }

  @Test
  void supersedesOnChangedCost() {
    RecipeCostInput first = input(new BigDecimal("6.00"));
    CanonicalRecipeCost existing =
        CanonicalRecipeCost.create(
            UUID.nameUUIDFromBytes("recipe:1".getBytes(StandardCharsets.UTF_8)),
            "CTB",
            "recipe-cost:1",
            RAW,
            Instant.parse("2026-10-12T00:00:00Z"),
            Instant.parse("2026-10-12T00:00:00Z"),
            first.recipeId(),
            first.recipeName(),
            first.costPerPortion(),
            first.costPerPortionInclLabour(),
            first.totalCost(),
            first.portionSize(),
            first.portionSizeValue());
    when(repository.lockCurrentRecipeCost("recipe-cost:1", "CTB"))
        .thenReturn(Optional.of(existing));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.recordAt(input(new BigDecimal("7.50")), Instant.parse("2026-10-12T01:00:00Z"));

    assertThat(existing.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(existing);
    verify(repository).save(any(CanonicalRecipeCost.class));
  }

  @Test
  void sameFactIsANoop() {
    RecipeCostInput first = input(new BigDecimal("6.00"));
    when(repository.lockCurrentRecipeCost("recipe-cost:1", "CTB")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalRecipeCost saved = service.record(first);

    when(repository.lockCurrentRecipeCost("recipe-cost:1", "CTB")).thenReturn(Optional.of(saved));
    CanonicalRecipeCost again = service.record(first);

    assertThat(again).isSameAs(saved);
    verify(repository, times(1)).save(any(CanonicalRecipeCost.class));
  }

  @Test
  void logicalIdIsDeterministic() {
    when(repository.lockCurrentRecipeCost("recipe-cost:1", "CTB")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalRecipeCost saved = service.record(input(new BigDecimal("6.00")));

    assertThat(saved.logicalEntityId())
        .isEqualTo(UUID.nameUUIDFromBytes("recipe:1".getBytes(StandardCharsets.UTF_8)));
  }
}
