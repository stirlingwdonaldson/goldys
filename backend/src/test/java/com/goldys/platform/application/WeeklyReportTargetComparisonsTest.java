package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyReportTargetComparisonsTest {
  private final LocalDate monday = LocalDate.parse("2026-10-26");
  private final List<LocalDate> dates = monday.datesUntil(monday.plusDays(7)).toList();

  private WeeklyReportTargets.Target target(String id, String value) {
    return new WeeklyReportTargets.Target(id, new BigDecimal(value), monday, "reviewed");
  }

  private Sale sale(LocalDate date, String value, String nature) {
    return new Sale(date, nature, new BigDecimal(value), new BigDecimal(value), "CTB", nature);
  }

  @Test
  void usesIndependentHeadlineAndKnownClassifiedSubtotalsForPartialComparisons() {
    var sales =
        List.of(
            sale(monday, "100", "FOOD"), sale(monday, "50", "BEVERAGE"),
            sale(monday.plusDays(1), "94", "FOOD"), sale(monday.plusDays(1), "6", "UNKNOWN"));
    var comparisons =
        WeeklyReportTargetComparisons.calculate(
            dates,
            sales,
            new BigDecimal("500"),
            List.of(
                target("sales_target", "400"),
                target("food_target", "200"),
                target("beverage_target", "40")));
    assertThat(comparisons)
        .containsExactly(
            new WeeklyReportTargetComparisons.Comparison(
                "sales",
                new BigDecimal("400"),
                new BigDecimal("100"),
                new BigDecimal("0.25000000")),
            new WeeklyReportTargetComparisons.Comparison(
                "food",
                new BigDecimal("200"),
                new BigDecimal("-100"),
                new BigDecimal("-0.50000000")),
            new WeeklyReportTargetComparisons.Comparison(
                "beverage",
                new BigDecimal("40"),
                new BigDecimal("10"),
                new BigDecimal("0.25000000")));
  }

  @Test
  void zeroTargetWithholdsPercentWithoutWithholdingSignedDifference() {
    var comparisons =
        WeeklyReportTargetComparisons.calculate(
            dates, List.of(sale(monday, "-10", "FOOD")), null, List.of(target("food_target", "0")));
    assertThat(comparisons)
        .containsExactly(
            new WeeklyReportTargetComparisons.Comparison(
                "food", BigDecimal.ZERO, new BigDecimal("-10"), null));
  }

  @Test
  void absentActualsAndAbsentTargetsDoNotProduceComparisons() {
    assertThat(
            WeeklyReportTargetComparisons.calculate(
                dates,
                List.of(),
                null,
                List.of(
                    target("sales_target", "100"),
                    target("food_target", "100"),
                    target("beverage_target", "100"))))
        .isEmpty();
    assertThat(
            WeeklyReportTargetComparisons.calculate(
                dates, List.of(sale(monday, "100", "FOOD")), new BigDecimal("500"), List.of()))
        .isEmpty();
  }
}
