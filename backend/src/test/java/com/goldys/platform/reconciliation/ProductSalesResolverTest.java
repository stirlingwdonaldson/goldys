package com.goldys.platform.reconciliation;

import static com.goldys.platform.reconciliation.ProductSalesResolver.classify;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductSalesResolverTest {

  @Test
  void classifiesSingleAgreedAndConflict() {
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "10", "100.00"))))
        .isEqualTo("agreed");
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "11", "100.00"))))
        .isEqualTo("conflict");
    // A single source is trusted, not a conflict (sources don't overlap 1:1 for products).
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00")))).isEqualTo("single");
  }

  @Test
  void agreementToleratesOneCentAmountRoundingButNotQuantityDrift() {
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "10", "99.99"))))
        .isEqualTo("agreed");
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "10", "99.98"))))
        .isEqualTo("conflict");
    // Quantity must still match exactly even when amount is within a cent.
    assertThat(
            classify(
                List.of(st("LIGHTSPEED", "10.0000", "100.00"), st("CTB", "10.0001", "100.00"))))
        .isEqualTo("conflict");
  }

  @Test
  void singleSourceResolvesToThatSource() {
    var result =
        ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88")), Optional.empty(), Optional.empty())
            .orElseThrow();

    assertThat(result.resolutionType()).isEqualTo("single");
    assertThat(result.authoritativeSource()).isEqualTo("LIGHTSPEED");
    assertThat(result.quantitySold()).isEqualByComparingTo("150");
    assertThat(result.amount()).isEqualByComparingTo("380.88");
    assertThat(result.hasConflict()).isFalse();
  }

  @Test
  void agreeingSourcesResolveToOneValueNotTheirSum() {
    var result =
        ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "1232", "17340.98"), st("CTB", "1232", "17340.98")),
                Optional.empty(),
                Optional.empty())
            .orElseThrow();

    assertThat(result.resolutionType()).isEqualTo("agreed");
    assertThat(result.authoritativeSource()).isEqualTo("agreed");
    assertThat(result.quantitySold()).isEqualByComparingTo("1232");
    assertThat(result.amount()).isEqualByComparingTo("17340.98");
    assertThat(result.hasConflict()).isFalse();
  }

  @Test
  void overrideSelectsOneSource() {
    var result =
        ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88"), st("CTB", "127", "322.46")),
                Optional.of("CTB"),
                Optional.empty())
            .orElseThrow();

    assertThat(result.resolutionType()).isEqualTo("override");
    assertThat(result.authoritativeSource()).isEqualTo("CTB");
    assertThat(result.quantitySold()).isEqualByComparingTo("127");
    assertThat(result.amount()).isEqualByComparingTo("322.46");
    assertThat(result.hasConflict()).isFalse();
  }

  @Test
  void conflictWithNoRuleIsUnresolved() {
    var result =
        ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88"), st("CTB", "127", "322.46")),
                Optional.empty(),
                Optional.empty())
            .orElseThrow();

    assertThat(result.resolutionType()).isEqualTo("conflict");
    assertThat(result.authoritativeSource()).isNull();
    assertThat(result.quantitySold()).isNull();
    assertThat(result.amount()).isNull();
    assertThat(result.hasConflict()).isTrue();
  }

  @Test
  void emptySourcesAreNotResolved() {
    assertThat(ProductSalesResolver.resolve(List.of(), Optional.empty(), Optional.empty()))
        .isEmpty();
  }

  @Test
  void priorityRuleSelectsOneSource() {
    ResolutionRule rule =
        ResolutionRule.create(
            "product_sales",
            "garlic aioli",
            "priority",
            null,
            List.of("CTB"),
            "a@b.com",
            Instant.EPOCH);

    var result =
        ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88"), st("CTB", "127", "322.46")),
                Optional.empty(),
                Optional.of(rule))
            .orElseThrow();

    assertThat(result.resolutionType()).isEqualTo("rule");
    assertThat(result.authoritativeSource()).isEqualTo("CTB");
    assertThat(result.quantitySold()).isEqualByComparingTo("127");
    assertThat(result.amount()).isEqualByComparingTo("322.46");
    assertThat(result.hasConflict()).isFalse();
  }

  private static ProductSourceTotal st(String source, String qty, String amount) {
    return new ProductSourceTotal(source, new BigDecimal(qty), new BigDecimal(amount), null);
  }
}
