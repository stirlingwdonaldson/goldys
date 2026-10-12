package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CanonicalSaleRecipeLinkServiceTest {

  private static final UUID RAW = UUID.randomUUID();

  private CanonicalSaleRecipeLinkRepository repository;
  private ApplicationEventPublisher publisher;
  private CanonicalSaleRecipeLinkService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalSaleRecipeLinkRepository.class);
    publisher = mock(ApplicationEventPublisher.class);
    service = new CanonicalSaleRecipeLinkService(repository, publisher);
  }

  private static SaleRecipeLinkInput input(String folder) {
    return new SaleRecipeLinkInput(
        "CTB", 9001, "STOCK-1", "Kids Fish", 1, "Kids Fish", folder, true, RAW);
  }

  @Test
  void recordsNewFactAndPublishesEvent() {
    when(repository.lockCurrentSaleRecipeLink("sale-recipe-link:9001", "CTB"))
        .thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalSaleRecipeLink saved = service.record(input("Food"));

    assertThat(saved.recipeId()).isEqualTo(1);
    assertThat(saved.recipeFolderName()).isEqualTo("Food");
    verify(publisher).publishEvent(new SaleRecipeLinkRecorded(1));
  }

  @Test
  void supersedesOnChangedLink() {
    SaleRecipeLinkInput first = input("Food");
    CanonicalSaleRecipeLink existing =
        CanonicalSaleRecipeLink.create(
            UUID.nameUUIDFromBytes("sale-recipe-link:9001".getBytes(StandardCharsets.UTF_8)),
            "CTB",
            "sale-recipe-link:9001",
            RAW,
            Instant.parse("2026-10-12T00:00:00Z"),
            Instant.parse("2026-10-12T00:00:00Z"),
            first.saleItemRecipeId(),
            first.saleItemStockCode(),
            first.saleItemStockDescription(),
            first.recipeId(),
            first.recipeName(),
            first.recipeFolderName(),
            first.isRecipeActive());
    when(repository.lockCurrentSaleRecipeLink("sale-recipe-link:9001", "CTB"))
        .thenReturn(Optional.of(existing));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.recordAt(input("Dessert"), Instant.parse("2026-10-12T01:00:00Z"));

    assertThat(existing.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(existing);
  }

  @Test
  void sameFactIsANoop() {
    SaleRecipeLinkInput first = input("Food");
    when(repository.lockCurrentSaleRecipeLink("sale-recipe-link:9001", "CTB"))
        .thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CanonicalSaleRecipeLink saved = service.record(first);

    when(repository.lockCurrentSaleRecipeLink("sale-recipe-link:9001", "CTB"))
        .thenReturn(Optional.of(saved));
    CanonicalSaleRecipeLink again = service.record(first);

    assertThat(again).isSameAs(saved);
    verify(repository, times(1)).save(any(CanonicalSaleRecipeLink.class));
  }

  @Test
  void logicalIdIsDeterministic() {
    when(repository.lockCurrentSaleRecipeLink("sale-recipe-link:9001", "CTB"))
        .thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalSaleRecipeLink saved = service.record(input("Food"));

    assertThat(saved.logicalEntityId())
        .isEqualTo(
            UUID.nameUUIDFromBytes("sale-recipe-link:9001".getBytes(StandardCharsets.UTF_8)));
  }
}
