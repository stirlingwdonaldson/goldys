package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.semantic.WeeklyInventoryQuery.Waste;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyReportInventoryTest {
  @Test
  void includesUnknownReasonsAndNormalizesOnlyKnownVariants() {
    var d = LocalDate.parse("2026-10-26");
    var result =
        WeeklyReportInventory.summarize(
            List.of(
                new Waste(d, null, 1, 1, new BigDecimal("40")),
                new Waste(d, "Knockoff", 1, 1, new BigDecimal("10")),
                new Waste(d, " knock off ", 2, 1, new BigDecimal("50"))));
    assertThat(result.lines()).isEqualTo(4);
    assertThat(result.valuedLines()).isEqualTo(3);
    assertThat(result.value()).isEqualByComparingTo("100");
    assertThat(result.reasonCoverageLines()).isEqualByComparingTo("0.75");
    assertThat(result.reasonCoverageValue()).isEqualByComparingTo("0.6");
    assertThat(result.reasons()).hasSize(2);
    assertThat(result.reasons().getLast().originalReasons())
        .containsExactly(" knock off ", "Knockoff");
  }

  @Test
  void noRecordsIsUnknownWhileAllMissingReasonsHasZeroCoverage() {
    var empty = WeeklyReportInventory.summarize(List.of());
    assertThat(empty.value()).isNull();
    assertThat(empty.reasonCoverageLines()).isNull();
    var missing =
        WeeklyReportInventory.summarize(
            List.of(new Waste(LocalDate.now(), " ", 1, 1, BigDecimal.TEN)));
    assertThat(missing.reasonCoverageLines()).isZero();
    assertThat(missing.reasonCoverageValue()).isZero();
    assertThat(missing.reasons().getFirst().reason()).isEqualTo("No reason recorded");
  }

  @Test
  void classificationKeepsUnmatchedLinesAndWithholdsRatesForMissingCosts() {
    var d = LocalDate.parse("2026-10-26");
    var dates = d.datesUntil(d.plusDays(7)).toList();
    var empty = java.util.Collections.<BigDecimal>nCopies(7, null);
    var sales =
        new WeeklyReport.Row(
            "food_sales",
            "Sales",
            "Food",
            "AUD",
            java.util.Collections.nCopies(7, BigDecimal.TEN),
            new BigDecimal("70"),
            "QUALIFIED",
            List.of());
    var bev =
        new WeeklyReport.Row(
            "beverage_sales",
            "Sales",
            "Beverage",
            "AUD",
            sales.days(),
            sales.total(),
            "QUALIFIED",
            List.of());
    var net =
        new WeeklyReport.Row(
            "net_sales",
            "Sales",
            "Net",
            "AUD",
            sales.days(),
            sales.total(),
            "QUALIFIED",
            List.of());
    var rate =
        new WeeklyReport.Row(
            "food_wastage_percent",
            "Wastage",
            "Rate",
            "PERCENT",
            empty,
            null,
            "UNAVAILABLE",
            List.of());
    var observations =
        List.of(
            new Waste(d, "KO", 2, 1, BigDecimal.ONE, "FOOD"),
            new Waste(d, null, 1, 1, new BigDecimal("3"), "UNKNOWN"));
    var rows = WeeklyReportInventory.fill(List.of(sales, bev, net, rate), dates, observations);
    var result =
        rows.stream().filter(r -> r.id().equals("food_wastage_percent")).findFirst().orElseThrow();
    assertThat(result.total()).isNull();
    assertThat(result.days()).containsOnlyNulls();
    assertThat(
            rows.stream()
                .filter(r -> r.id().equals("unclassified_wastage"))
                .findFirst()
                .orElseThrow()
                .total())
        .isEqualByComparingTo("3");
    assertThat(
            rows.stream()
                .filter(r -> r.id().equals("wastage_classified_coverage_lines"))
                .findFirst()
                .orElseThrow()
                .total())
        .isEqualByComparingTo("0.66666667");
  }
}
