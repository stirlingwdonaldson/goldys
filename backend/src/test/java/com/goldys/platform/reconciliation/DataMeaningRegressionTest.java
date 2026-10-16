package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataMeaningRegressionTest {
  @Test
  void equalGrossCannotHideDifferentTaxAndNetInEitherSourceOrder() {
    SourceTotal a =
        new SourceTotal(
            "A", new BigDecimal("110"), new BigDecimal("10"), new BigDecimal("100"), Instant.EPOCH);
    SourceTotal b =
        new SourceTotal(
            "B", new BigDecimal("110"), BigDecimal.ZERO, new BigDecimal("110"), Instant.EPOCH);
    for (List<SourceTotal> sources : List.of(List.of(a, b), List.of(b, a))) {
      var result =
          DailySalesResolver.resolve(sources, Optional.empty(), Optional.empty()).orElseThrow();
      assertThat(result.hasConflict()).isTrue();
      assertThat(result.net()).isNull();
    }
  }

  @Test
  void missingTaxDoesNotAgreeWithKnownTax() {
    SourceTotal a =
        new SourceTotal(
            "A", new BigDecimal("110"), new BigDecimal("10"), new BigDecimal("100"), Instant.EPOCH);
    SourceTotal b = new SourceTotal("B", new BigDecimal("110"), null, null, Instant.EPOCH);
    assertThat(
            DailySalesResolver.resolve(List.of(a, b), Optional.empty(), Optional.empty())
                .orElseThrow()
                .hasConflict())
        .isTrue();
  }
}
