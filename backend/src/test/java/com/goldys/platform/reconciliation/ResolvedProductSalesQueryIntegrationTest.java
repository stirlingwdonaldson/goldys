package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ResolvedProductSalesQueryIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedProductSalesRepository repository;
  @Autowired ProductMetricsQuery query;

  @BeforeEach
  void seed() {
    jdbc.update("truncate table resolved_product_sales");
    repository.save(row(SEP_13, "chips", "10", "50.00", "agreed", "agreed", false));
    repository.save(row(SEP_13, "garlic aioli", "150", "380.88", "agreed", "agreed", false));
    repository.save(row(SEP_13, "parma", null, null, "conflict", null, true));
    repository.save(row(SEP_14, "chips", "20", "100.00", "agreed", "agreed", false));
    repository.save(row(SEP_14, "garlic aioli", null, null, "conflict", null, true));
  }

  @Test
  void productSalesReturnsTheResolvedRow() {
    var chips = query.productSales(SEP_14, "chips");
    assertThat(chips).isPresent();
    assertThat(chips.get().amount()).isEqualByComparingTo("100.00");
    assertThat(chips.get().authoritativeSource()).isEqualTo("agreed");
  }

  @Test
  void productSalesReflectsUnresolvedConflict() {
    var garlic = query.productSales(SEP_14, "garlic aioli");
    assertThat(garlic).isPresent();
    assertThat(garlic.get().hasConflict()).isTrue();
    assertThat(garlic.get().amount()).isNull();
  }

  @Test
  void productSalesReturnsRowsOrderedByDateThenProduct() {
    var rows = query.productSales(SEP_13, SEP_14);
    assertThat(rows).hasSize(5);
    assertThat(rows.get(0).tradingDate()).isEqualTo(SEP_13);
    assertThat(rows.get(0).productName()).isEqualTo("chips");
  }

  @Test
  void topSellersSumsResolvedRowsAndFlagsUnresolved() {
    var top = query.topSellers(SEP_13, SEP_14, 10);
    assertThat(top).hasSize(3);

    // garlic aioli: resolved SEP_13 row sums; the unresolved SEP_14 row is flagged, not summed.
    assertThat(top.get(0).productName()).isEqualTo("garlic aioli");
    assertThat(top.get(0).amount()).isEqualByComparingTo("380.88");
    assertThat(top.get(0).hasConflict()).isTrue();

    assertThat(top.get(1).productName()).isEqualTo("chips");
    assertThat(top.get(1).amount()).isEqualByComparingTo("150.00");
    assertThat(top.get(1).quantitySold()).isEqualByComparingTo("30");
    assertThat(top.get(1).hasConflict()).isFalse();
  }

  @Test
  void topSellersSurfacesUnresolvedOnlyProducts() {
    var top = query.topSellers(SEP_13, SEP_14, 10);
    var parma = top.stream().filter(t -> t.productName().equals("parma")).findFirst();
    assertThat(parma).isPresent();
    assertThat(parma.get().amount()).isNull();
    assertThat(parma.get().quantitySold()).isNull();
    assertThat(parma.get().hasConflict()).isTrue();
  }

  @Test
  void openConflictsCountsUnresolvedProductDays() {
    assertThat(query.openConflicts()).isEqualTo(2);
  }

  private static ResolvedProductSales row(
      LocalDate date,
      String key,
      String qty,
      String amount,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict) {
    return new ResolvedProductSales(
        date,
        key,
        qty == null ? null : new BigDecimal(qty),
        amount == null ? null : new BigDecimal(amount),
        resolutionType,
        authoritativeSource,
        hasConflict,
        Instant.EPOCH);
  }
}
