package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CanonicalSaleRecipeLinkServiceTest {

  private static final UUID RAW = UUID.randomUUID();

  private final List<CanonicalSaleRecipeLink> rows = new ArrayList<>();

  private CanonicalSaleRecipeLinkRepository repository;
  private ApplicationEventPublisher publisher;
  private CanonicalSaleRecipeLinkService service;

  @BeforeEach
  void setUp() {
    rows.clear();
    repository = mock(CanonicalSaleRecipeLinkRepository.class);
    publisher = mock(ApplicationEventPublisher.class);
    service = new CanonicalSaleRecipeLinkService(repository, publisher);
  }

  private static SaleRecipeLinkInput input(String folder) {
    return new SaleRecipeLinkInput(
        "CTB", 9001, "STOCK-1", "Kids Fish", 1, "Kids Fish", folder, true, null, RAW);
  }

  /** Stub save/lock against an in-memory list so supersession chains are observable. */
  private void trackRows() {
    when(repository.save(any()))
        .thenAnswer(
            inv -> {
              CanonicalSaleRecipeLink link = inv.getArgument(0);
              rows.add(link);
              return link;
            });
    when(repository.lockCurrentSaleRecipeLink(anyString(), anyString()))
        .thenAnswer(
            inv -> {
              String ref = inv.getArgument(0);
              return rows.stream()
                  .filter(l -> l.supersededAt() == null && l.sourceRecordRef().equals(ref))
                  .findFirst();
            });
    when(repository.lockCurrentBySaleItemRecipeId(anyInt()))
        .thenAnswer(
            inv -> {
              int id = inv.getArgument(0);
              return rows.stream()
                  .filter(l -> l.supersededAt() == null && l.saleItemRecipeId() == id)
                  .findFirst();
            });
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
            first.isRecipeActive(),
            null);
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

  @Test
  void setProductIdSupersedesWhenChanged() {
    trackRows();
    service.record(input("Food"));

    service.setProductId(9001, 123);

    CanonicalSaleRecipeLink superseded = rows.get(0);
    CanonicalSaleRecipeLink successor = rows.get(1);
    assertThat(superseded.supersededAt()).isNotNull();
    assertThat(superseded.lightspeedProductId()).isNull();
    assertThat(successor.supersededAt()).isNull();
    assertThat(successor.lightspeedProductId()).isEqualTo(123);
    assertThat(successor.logicalEntityId()).isEqualTo(superseded.logicalEntityId());
    assertThat(successor.rawRecordId()).isEqualTo(superseded.rawRecordId());
  }

  @Test
  void setProductIdIsIdempotent() {
    trackRows();
    service.record(input("Food"));

    service.setProductId(9001, 123);
    service.setProductId(9001, 123);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).supersededAt()).isNotNull();
    assertThat(rows.get(1).supersededAt()).isNull();
    assertThat(rows.get(1).lightspeedProductId()).isEqualTo(123);
    verify(publisher, times(2)).publishEvent(new SaleRecipeLinkRecorded(1));
  }

  @Test
  void setProductIdClearsWhenUnmatched() {
    trackRows();
    service.record(input("Food"));
    service.setProductId(9001, 123);

    service.setProductId(9001, null);

    assertThat(rows).hasSize(3);
    CanonicalSaleRecipeLink current =
        rows.stream().filter(l -> l.supersededAt() == null).findFirst().orElseThrow();
    assertThat(current.lightspeedProductId()).isNull();
  }

  @Test
  void recordCarriesForwardProductIdOnSupersede() {
    Instant t0 = Instant.parse("2026-10-12T00:00:00Z");
    Instant t1 = Instant.parse("2026-10-12T01:00:00Z");
    CanonicalSaleRecipeLink existing =
        CanonicalSaleRecipeLink.create(
            UUID.nameUUIDFromBytes("sale-recipe-link:9001".getBytes(StandardCharsets.UTF_8)),
            "CTB",
            "sale-recipe-link:9001",
            RAW,
            t0,
            t0,
            9001,
            "STOCK-1",
            "Kids Fish",
            1,
            "Kids Fish",
            "Food",
            true,
            123);
    when(repository.lockCurrentSaleRecipeLink("sale-recipe-link:9001", "CTB"))
        .thenReturn(Optional.of(existing));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.recordAt(input("Dessert"), t1);

    ArgumentCaptor<CanonicalSaleRecipeLink> captor =
        ArgumentCaptor.forClass(CanonicalSaleRecipeLink.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().lightspeedProductId()).isEqualTo(123);
  }
}
