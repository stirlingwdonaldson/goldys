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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CanonicalRecipeIngredientServiceTest {

  private static final UUID RAW = UUID.randomUUID();
  private static final String REF = "recipe-ingredient:1:100:42";

  private CanonicalRecipeIngredientRepository repository;
  private ApplicationEventPublisher publisher;
  private CanonicalRecipeIngredientService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalRecipeIngredientRepository.class);
    publisher = mock(ApplicationEventPublisher.class);
    service = new CanonicalRecipeIngredientService(repository, publisher);
  }

  private static RecipeIngredientInput input(BigDecimal portionWeight) {
    return new RecipeIngredientInput(
        "CTB",
        1,
        1,
        "Parma",
        false,
        100,
        42,
        "CHICKEN-1",
        "Chicken breast",
        "Chicken breast",
        "Acme",
        "ACME-1",
        portionWeight,
        "grams",
        "g",
        new BigDecimal("6.00"),
        new BigDecimal("2.00"),
        new BigDecimal("1.00"),
        new BigDecimal("0.10"),
        false,
        RAW);
  }

  @Test
  void recordsNewFactAndPublishesEvent() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalRecipeIngredient saved = service.record(input(new BigDecimal("6.00")));

    assertThat(saved.parentRecipeId()).isEqualTo(1);
    assertThat(saved.portionWeight()).isEqualByComparingTo("6.00");
    verify(publisher).publishEvent(new RecipeIngredientRecorded(1));
  }

  @Test
  void supersedesOnChangedFact() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalRecipeIngredient saved =
        service.recordAt(input(new BigDecimal("6.00")), Instant.parse("2026-10-12T00:00:00Z"));

    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.of(saved));
    service.recordAt(input(new BigDecimal("7.50")), Instant.parse("2026-10-12T01:00:00Z"));

    assertThat(saved.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(saved);
    verify(repository, times(2)).save(any(CanonicalRecipeIngredient.class));
  }

  @Test
  void sameFactIsANoop() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalRecipeIngredient saved = service.record(input(new BigDecimal("6.00")));

    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.of(saved));
    CanonicalRecipeIngredient again = service.record(input(new BigDecimal("6.00")));

    assertThat(again).isSameAs(saved);
    verify(repository, times(1)).save(any(CanonicalRecipeIngredient.class));
  }

  @Test
  void logicalIdIsDeterministic() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalRecipeIngredient saved = service.record(input(new BigDecimal("6.00")));

    assertThat(saved.logicalEntityId())
        .isEqualTo(UUID.nameUUIDFromBytes(REF.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void recordAllPublishesOneEvent() {
    when(repository.lockCurrentSourceFact(eq("CTB"), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.recordAll(List.of(input(new BigDecimal("6.00")), input(new BigDecimal("7.00"))));

    verify(repository, times(2)).save(any());
    verify(publisher, times(1)).publishEvent(any(RecipeIngredientRecorded.class));
  }
}
