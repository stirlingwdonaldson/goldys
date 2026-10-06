package com.goldys.platform.reconciliation;

import static com.goldys.platform.reconciliation.DailySalesResolver.classify;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailySalesResolverTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  @Test
  void classifiesAgreedConflictAndMissing() {
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00"), st("CTB", "10.00"))))
        .isEqualTo("agreed");
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00"), st("CTB", "10.005"))))
        .isEqualTo("agreed");
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00"), st("CTB", "12.00"))))
        .isEqualTo("conflict");
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00")))).isEqualTo("missing");
  }

  @Test
  void overrideResolvesToThatSource() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "27650.66"), st("CTB", "20990.83")),
            Optional.of("CTB"),
            Optional.empty());

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("override");
    assertThat(r.get().authoritativeSource()).isEqualTo("CTB");
    assertThat(r.get().totalSales()).isEqualByComparingTo("20990.83");
    assertThat(r.get().hasConflict()).isFalse();
  }

  @Test
  void agreementResolvesToFirstSourceValue() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "9694.80"), st("CTB", "9694.80")),
            Optional.empty(),
            Optional.empty());

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("agreed");
    assertThat(r.get().authoritativeSource()).isEqualTo("agreed");
    assertThat(r.get().totalSales()).isEqualByComparingTo("9694.80");
  }

  @Test
  void conflictWithNoRuleIsUnresolved() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "27650.66"), st("CTB", "20990.83")),
            Optional.empty(),
            Optional.empty());

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("conflict");
    assertThat(r.get().totalSales()).isNull();
    assertThat(r.get().hasConflict()).isTrue();
  }

  @Test
  void singleSourceIsMissingNotResolved() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "27650.66")), Optional.empty(), Optional.empty());

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("missing");
    assertThat(r.get().totalSales()).isNull();
    assertThat(r.get().hasConflict()).isTrue();
  }

  @Test
  void priorityRuleResolvesAConflict() {
    ResolutionRule rule =
        ResolutionRule.create(
            "daily_sales",
            "daily_sales",
            "priority",
            null,
            List.of("CTB"),
            "a@b.com",
            Instant.EPOCH);

    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "27650.66"), st("CTB", "20990.83")),
            Optional.empty(),
            Optional.of(rule));

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("rule");
    assertThat(r.get().authoritativeSource()).isEqualTo("CTB");
    assertThat(r.get().totalSales()).isEqualByComparingTo("20990.83");
  }

  @Test
  void emptySourcesResolveToNothing() {
    assertThat(DailySalesResolver.resolve(List.of(), Optional.empty(), Optional.empty())).isEmpty();
  }

  @Test
  void overrideForUnknownSourceDoesNotNpe() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("CTB", "20990.83")), Optional.of("LIGHTSPEED"), Optional.empty());
    // The override names a source with no row; the result is empty rather than an exception.
    assertThat(r).isEmpty();
  }

  private static SourceTotal st(String source, String total) {
    return new SourceTotal(source, new BigDecimal(total), null);
  }
}
