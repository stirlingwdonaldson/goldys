package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import com.goldys.platform.semantic.WeeklyInventoryQuery.Waste;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyReportCalculationTest {
  @Test
  void keepsIndependentHeadlineAndTaxContextConsistentAcrossWeeklyCalculations() {
    var monday = LocalDate.parse("2026-10-26");
    var dates = monday.datesUntil(monday.plusDays(7)).toList();
    var sales =
        dates.stream()
            .map(
                d ->
                    new Sale(
                        d,
                        "Food",
                        new BigDecimal("110"),
                        new BigDecimal("100"),
                        "CTB",
                        "FOOD",
                        new BigDecimal("25"),
                        1,
                        1))
            .toList();
    var waste = dates.stream().map(d -> new Waste(d, "KO", 1, 1, BigDecimal.ONE, "FOOD")).toList();
    var headline = new HashMap<LocalDate, BigDecimal>();
    for (var date : dates) headline.put(date, new BigDecimal("200"));
    headline.put(monday, null);
    var context =
        new WeeklyReportCalculation.Context(dates, headline, new BigDecimal("1500"), false);
    var targets =
        List.of(
            new WeeklyReportTargets.Target(
                "sales_target", new BigDecimal("1400"), monday, "reviewed"));
    var result = WeeklyReportCalculation.calculate(context, sales, waste, targets);
    var rows =
        result.rows().stream()
            .collect(java.util.stream.Collectors.toMap(WeeklyReport.Row::id, r -> r));
    assertThat(rows.get("food_sales").total()).isEqualByComparingTo("700");
    assertThat(rows.get("sales_reconciliation_difference").days().getFirst()).isNull();
    assertThat(rows.get("sales_reconciliation_difference").total()).isEqualByComparingTo("600");
    assertThat(rows.get("food_theoretical_cogs").total()).isEqualByComparingTo("175");
    assertThat(rows.get("food_cogs_percent").days()).containsOnlyNulls();
    assertThat(rows.get("food_cogs_percent").total()).isNull();
    assertThat(rows.get("total_wastage_percent").days().getFirst()).isNull();
    assertThat(rows.get("total_wastage_percent").total()).isEqualByComparingTo("0.00466667");
    assertThat(rows.get("food_wastage_percent").total()).isEqualByComparingTo("0.01");
    assertThat(result.wastage().value()).isEqualByComparingTo("7");
    assertThat(result.comparisons())
        .containsExactly(
            new WeeklyReportTargetComparisons.Comparison(
                "sales",
                new BigDecimal("1400"),
                new BigDecimal("100"),
                new BigDecimal("0.07142857")));
    assertThat(WeeklyReportCalculation.calculate(context, sales, waste, targets)).isEqualTo(result);
    assertThat(headline).hasSize(7).containsEntry(monday, null);
  }
}
