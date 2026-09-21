package com.goldys.platform.reconciliation;

import static com.goldys.platform.reconciliation.ProductSalesReconciliationService.classify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductSalesReconciliationTest {

  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Test
  void classifiesAgreedConflictAndMissing() {
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "10", "100.00"))))
        .isEqualTo("agreed");
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "9", "90.00"))))
        .isEqualTo("conflict"); // both fields differ — one conflict, not two
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"))))
        .isEqualTo("missing"); // source-only product is surfaced, not dropped
  }

  @Test
  void conflictsGroupByProductAndDate() {
    CanonicalProductSalesQuery query = mock(CanonicalProductSalesQuery.class);
    when(query.currentProductSales())
        .thenReturn(
            List.of(
                view("LIGHTSPEED", SEP_14, "pint carlton draught", "1232", "17340.98"),
                view("CTB", SEP_14, "pint carlton draught", "1232", "17340.98"), // agree
                view("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"),
                view("CTB", SEP_14, "garlic aioli", "127", "322.46"))); // conflict

    ProductSalesReconciliationService service =
        new ProductSalesReconciliationService(query, mock(ProductSalesOverrideRepository.class));

    List<ProductSalesConflict> conflicts = service.conflicts();
    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0).productNameKey()).isEqualTo("garlic aioli");
    assertThat(conflicts.get(0).status()).isEqualTo("conflict");
  }

  private static ProductSourceTotal st(String source, String qty, String amount) {
    return new ProductSourceTotal(source, new BigDecimal(qty), new BigDecimal(amount));
  }

  private static ProductSalesView view(
      String source, LocalDate date, String key, String qty, String amount) {
    return new ProductSalesView(source, date, key, new BigDecimal(qty), new BigDecimal(amount));
  }
}
