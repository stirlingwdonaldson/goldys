package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbProductSalesBatchTest {
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
    assertThatThrownBy(() -> batch.add(item("A", null, "3"), UUID.randomUUID()))
        .hasMessageContaining("stable group/product");
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
