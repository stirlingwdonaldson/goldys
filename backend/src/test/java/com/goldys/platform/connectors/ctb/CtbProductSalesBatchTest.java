package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbProductSalesBatchTest {
  @Test
  void retainedRowCollisionPreservesBothObservationsAndCountsEachOnce() throws Exception {
    byte[] bytes;
    try (var in = getClass().getResourceAsStream("/fixtures/ctb/sale-items-row-collision.json")) {
      bytes = in.readAllBytes();
    }
    var items = new CtbSaleItemParser().parse(bytes);
    var batch = new CtbProductSalesBatch(LocalDate.of(2026, 8, 6));
    UUID first = UUID.randomUUID(), overlap = UUID.randomUUID();
    items.forEach(i -> batch.add(i, first));
    items.forEach(i -> batch.add(i, overlap));
    assertThat(items).hasSize(118);
    assertThat(batch.sourceFacts()).hasSize(118);
    assertThat(batch.totals()).hasSize(117);
    assertThat(
            batch.totals().stream().map(t -> t.amount()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(
            items.stream().map(CtbSaleItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    var collision =
        batch.sourceFacts().stream()
            .filter(
                i ->
                    i.quantitySold().compareTo(new BigDecimal("14")) == 0
                        && i.amount().compareTo(new BigDecimal("136.2796")) == 0)
            .findFirst()
            .orElseThrow();
    assertThat(batch.totals())
        .filteredOn(t -> t.stockCode().equals(collision.stockCode()))
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.quantitySold()).isEqualByComparingTo("15");
              assertThat(t.amount()).isEqualByComparingTo("146.6001");
              assertThat(t.contributorRawIds()).containsExactly(first, overlap);
            });
    var changed = items.getFirst();
    assertThatThrownBy(
            () ->
                batch.add(
                    new CtbSaleItem(
                        changed.stockCode(),
                        changed.stockDescription(),
                        BigDecimal.ZERO,
                        changed.amount(),
                        changed.costPerUnit(),
                        changed.foodCost(),
                        changed.totalFoodCost(),
                        changed.profit(),
                        changed.discountedAmount(),
                        changed.taxAmount(),
                        changed.unitPrice(),
                        changed.uniqueGroupId(),
                        changed.siteNumber(),
                        changed.uniqueId()),
                    overlap))
        .hasMessageContaining("group changed");
    assertThatThrownBy(() -> batch.add(item("X", null, "1"), overlap))
        .hasMessageContaining("mixes");
  }

  @Test
  void deduplicatesOverlappingPagesAndRetainsEveryReceipt() {
    var batch = new CtbProductSalesBatch(LocalDate.of(2026, 10, 1));
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    batch.add(item("A", "G1", "3"), a);
    batch.add(item("A", "G1", "3"), b);
    batch.add(item("A", "G2", "2"), b);
    batch.add(item("B", "G3", "7"), b);
    var totals = batch.totals();
    assertThat(totals).hasSize(2);
    assertThat(totals.getFirst().quantitySold()).isEqualByComparingTo("5");
    assertThat(totals.getFirst().contributorRawIds()).containsExactly(a, b);
    assertThat(totals.getLast().quantitySold()).isEqualByComparingTo("7");
  }

  @Test
  void changedOrUnidentifiedGroupsCannotProduceAmbiguousTotals() {
    var batch = new CtbProductSalesBatch(LocalDate.of(2026, 10, 1));
    batch.add(item("A", "G1", "3"), UUID.randomUUID());
    assertThatThrownBy(() -> batch.add(item("A", "G1", "4"), UUID.randomUUID()))
        .hasMessageContaining("group changed");
    assertThatThrownBy(() -> batch.add(item(null, "G2", "3"), UUID.randomUUID()))
        .hasMessageContaining("stable group/product");
  }

  @Test
  void ungroupedRowsDeduplicateButConflictingRepeatsStillFail() {
    var batch = new CtbProductSalesBatch(LocalDate.of(2026, 10, 1));
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    batch.add(item("A", null, "3"), first);
    batch.add(item("A", null, "3"), second);
    assertThat(batch.totals())
        .singleElement()
        .satisfies(
            total -> {
              assertThat(total.quantitySold()).isEqualByComparingTo("3");
              assertThat(total.contributorRawIds()).containsExactly(first, second);
            });
    assertThatThrownBy(() -> batch.add(item("A", null, "4"), second))
        .hasMessageContaining("group changed");
  }

  @Test
  void retainedNullGroupSampleProducesAllProductTotals() throws Exception {
    byte[] bytes;
    try (var in = getClass().getResourceAsStream("/fixtures/ctb/sale-items-null-groups.json")) {
      bytes = in.readAllBytes();
    }
    var items = new CtbSaleItemParser().parse(bytes);
    var batch = new CtbProductSalesBatch(LocalDate.of(2026, 7, 25));
    UUID raw = UUID.randomUUID();
    items.forEach(item -> batch.add(item, raw));
    assertThat(items)
        .hasSize(186)
        .allSatisfy(item -> assertThat(item.uniqueGroupId()).isNullOrEmpty());
    assertThat(batch.totals()).hasSize(186);
    assertThat(
            batch.totals().stream().map(t -> t.amount()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(
            items.stream().map(CtbSaleItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  private CtbSaleItem item(String code, String group, String qty) {
    return new CtbSaleItem(
        code,
        "Same name",
        new BigDecimal(qty),
        new BigDecimal(qty).multiply(BigDecimal.TEN),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        group,
        1);
  }
}
