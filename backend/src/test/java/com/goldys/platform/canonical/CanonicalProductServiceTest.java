package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CanonicalProductServiceTest {

  private static final UUID RAW = UUID.randomUUID();

  private final List<CanonicalProduct> saved = new ArrayList<>();

  private CanonicalProductRepository repository;
  private ApplicationEventPublisher publisher;
  private CanonicalProductService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalProductRepository.class);
    publisher = mock(ApplicationEventPublisher.class);
    service = new CanonicalProductService(repository, publisher);

    when(repository.save(any()))
        .thenAnswer(
            inv -> {
              CanonicalProduct p = inv.getArgument(0);
              saved.add(p);
              return p;
            });
    when(repository.lockCurrentProduct(anyString(), anyString()))
        .thenAnswer(
            inv -> {
              String ref = inv.getArgument(0);
              return saved.stream()
                  .filter(p -> p.supersededAt() == null && p.sourceRecordRef().equals(ref))
                  .findFirst();
            });
    when(repository.lockAllCurrent()).thenReturn(saved);
  }

  private static ProductInput input(int productId, String name, BigDecimal sellPriceIncTax) {
    return new ProductInput(
        "LIGHTSPEED", productId, name, "Beer", sellPriceIncTax, false, "Goldy's Bev", RAW);
  }

  @Test
  void recordAllSupersedesOnChange() {
    service.recordAll(List.of(input(1, "Keeper Pilsner", new BigDecimal("10.00"))));
    service.recordAll(List.of(input(1, "Keeper Pilsner", new BigDecimal("10.50"))));

    assertThat(saved).hasSize(2);
    assertThat(saved.get(0).supersededAt()).isNotNull();
    assertThat(saved.get(1).supersededAt()).isNull();
    assertThat(saved.get(1).logicalEntityId()).isEqualTo(saved.get(0).logicalEntityId());
  }

  @Test
  void recordAllIsIdempotentForUnchanged() {
    service.recordAll(List.of(input(1, "Keeper Pilsner", new BigDecimal("10.00"))));
    service.recordAll(List.of(input(1, "Keeper Pilsner", new BigDecimal("10.00"))));

    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).supersededAt()).isNull();
    verify(repository, times(1)).save(any(CanonicalProduct.class));
  }

  @Test
  void closeAbsentSupersedesMissingProducts() {
    service.recordAll(
        List.of(
            input(1, "Keeper Pilsner", new BigDecimal("10.00")),
            input(2, "Coke Zero", new BigDecimal("5.00")),
            input(3, "Parma", new BigDecimal("25.00"))));

    service.closeAbsent(Set.of(1, 3));

    assertThat(product(2).supersededAt()).isNotNull();
    assertThat(product(1).supersededAt()).isNull();
    assertThat(product(3).supersededAt()).isNull();
    verify(publisher).publishEvent(new ProductRecorded(2));
  }

  @Test
  void recordPublishesEvent() {
    service.recordAll(
        List.of(
            input(1, "Keeper Pilsner", new BigDecimal("10.00")),
            input(2, "Coke Zero", new BigDecimal("5.00"))));

    verify(publisher, times(1)).publishEvent(any(ProductRecorded.class));
  }

  @Test
  void currentProductsMapsToViews() {
    CanonicalProduct product =
        CanonicalProduct.create(
            UUID.nameUUIDFromBytes("lightspeed-product:1".getBytes(StandardCharsets.UTF_8)),
            "LIGHTSPEED",
            "lightspeed-product:1",
            RAW,
            Instant.parse("2026-10-13T00:00:00Z"),
            Instant.parse("2026-10-13T00:00:00Z"),
            1,
            "Keeper Pilsner",
            "Beer",
            new BigDecimal("10.00"),
            false,
            "Goldy's Bev");
    when(repository.findAllCurrent()).thenReturn(List.of(product));

    CanonicalProductQuery query = new CanonicalProductQuery(repository);
    List<ProductView> views = query.currentProducts();

    assertThat(views).hasSize(1);
    ProductView view = views.get(0);
    assertThat(view.productId()).isEqualTo(1);
    assertThat(view.productName()).isEqualTo("Keeper Pilsner");
    assertThat(view.category()).isEqualTo("Beer");
    assertThat(view.sellPriceIncTax()).isEqualByComparingTo("10.00");
    assertThat(view.isModifier()).isFalse();
    assertThat(view.reportingGroup()).isEqualTo("Goldy's Bev");
  }

  private CanonicalProduct product(int productId) {
    return saved.stream().filter(p -> p.productId() == productId).findFirst().orElseThrow();
  }
}
