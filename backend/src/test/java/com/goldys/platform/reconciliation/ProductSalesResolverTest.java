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
  void classifiesAgreedConflictAndMissingWithZeroTolerance() {
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "10", "100.00"))))
        .isEqualTo("agreed");
    // quantity differs by 0.0001 — zero tolerance, not "close enough"
    assertThat(
            classify(
                List.of(st("LIGHTSPEED", "10.0000", "100.00"), st("CTB", "10.0001", "100.00"))))
        .isEqualTo("conflict");
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00")))).isEqualTo("missing");
  }

  @Test
  void overrideResolvesThePair() {
    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88"), st("CTB", "127", "322.46")),
                Optional.of("CTB"),
                Optional.empty()))
        .isEmpty();
  }

  @Test
  void agreementResolvesThePair() {
    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "1232", "17340.98"), st("CTB", "1232", "17340.98")),
                Optional.empty(),
                Optional.empty()))
        .isEmpty();
  }

  @Test
  void conflictWithNoRuleIsAnException() {
    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88"), st("CTB", "127", "322.46")),
                Optional.empty(),
                Optional.empty()))
        .contains("conflict");
  }

  @Test
  void singleSourceIsMissing() {
    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88")), Optional.empty(), Optional.empty()))
        .contains("missing");
  }

  @Test
  void emptySourcesAreNotAnException() {
    assertThat(ProductSalesResolver.resolve(List.of(), Optional.empty(), Optional.empty()))
        .isEmpty();
  }

  @Test
  void aRuleResolvesThePair() {
    ResolutionRule rule =
        ResolutionRule.create(
            "product_sales",
            "garlic aioli",
            "priority",
            null,
            List.of("CTB"),
            "a@b.com",
            Instant.EPOCH);

    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88"), st("CTB", "127", "322.46")),
                Optional.empty(),
                Optional.of(rule)))
        .isEmpty();
  }

  private static ProductSourceTotal st(String source, String qty, String amount) {
    return new ProductSourceTotal(source, new BigDecimal(qty), new BigDecimal(amount), null);
  }
}
