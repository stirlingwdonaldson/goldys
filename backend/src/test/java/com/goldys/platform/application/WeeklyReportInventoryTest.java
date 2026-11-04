package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import com.goldys.platform.semantic.WeeklyInventoryQuery.Waste;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    var sales =
        dates.stream()
            .map(date -> new Sale(date, "Food", BigDecimal.TEN, BigDecimal.TEN, "CTB", "FOOD"))
            .toList();
    var observations =
        List.of(
            new Waste(d, "KO", 2, 1, BigDecimal.ONE, "FOOD"),
            new Waste(d, null, 1, 1, new BigDecimal("3"), "UNKNOWN"));
    var rows = WeeklyReportInventory.calculate(dates, observations, sales, Map.of(), null);
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

  @Test
  void calculatesCompleteRowsUsingIndependentHeadlineAndMatchingSales() {
    var start = LocalDate.parse("2026-10-26");
    var dates = start.datesUntil(start.plusDays(7)).toList();
    var sales =
        dates.stream()
            .map(d -> new Sale(d, "Food", BigDecimal.TEN, BigDecimal.TEN, "CTB", "FOOD"))
            .toList();
    var waste = dates.stream().map(d -> new Waste(d, "KO", 1, 1, BigDecimal.ONE, "FOOD")).toList();
    var headline =
        dates.stream()
            .collect(java.util.stream.Collectors.toMap(d -> d, d -> new BigDecimal("100")));
    var rows =
        WeeklyReportInventory.calculate(dates, waste, sales, headline, new BigDecimal("140"));
    assertThat(row(rows, "total_wastage").total()).isEqualByComparingTo("7");
    assertThat(row(rows, "total_wastage_percent").days())
        .allSatisfy(v -> assertThat(v).isEqualByComparingTo("0.01"));
    assertThat(row(rows, "total_wastage_percent").total()).isEqualByComparingTo("0.05");
    assertThat(row(rows, "food_wastage_percent").days())
        .allSatisfy(v -> assertThat(v).isEqualByComparingTo("0.1"));
    assertThat(row(rows, "food_wastage_percent").total()).isEqualByComparingTo("0.1");
    assertThat(rows)
        .extracting(WeeklyReport.Row::id)
        .containsExactly(
            "total_wastage",
            "total_wastage_percent",
            "wastage_reason_coverage_lines",
            "wastage_reason_coverage_value",
            "food_wastage",
            "food_wastage_percent",
            "beverage_wastage",
            "beverage_wastage_percent",
            "other_wastage",
            "unclassified_wastage",
            "wastage_classified_coverage_lines",
            "wastage_classified_coverage_value");
  }

  @Test
  void classificationHoldWithholdsMatchingRatesWithoutWithholdingKnownWastage() {
    var start = LocalDate.parse("2026-10-26");
    var dates = start.datesUntil(start.plusDays(7)).toList();
    var sales =
        new java.util.ArrayList<>(
            dates.stream()
                .map(d -> new Sale(d, "Food", BigDecimal.TEN, BigDecimal.TEN, "CTB", "FOOD"))
                .toList());
    sales.add(new Sale(start, "Unknown", BigDecimal.TEN, BigDecimal.TEN, "CTB", "UNKNOWN"));
    var waste = dates.stream().map(d -> new Waste(d, "KO", 1, 1, BigDecimal.ONE, "FOOD")).toList();
    var rows = WeeklyReportInventory.calculate(dates, waste, sales, Map.of(), null);
    assertThat(row(rows, "food_wastage").total()).isEqualByComparingTo("7");
    assertThat(row(rows, "food_wastage_percent").days().getFirst()).isNull();
    assertThat(row(rows, "food_wastage_percent").days().get(1)).isEqualByComparingTo("0.1");
    assertThat(row(rows, "food_wastage_percent").total()).isNull();
    assertThat(row(rows, "total_wastage_percent").total()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"FOOD", "BEVERAGE"})
  void zeroSalesDayWithholdsDailyWastageRateButRetainsSupportedWeeklyRate(String nature) {
    var rows = wastageWithFirstDaySales(nature, BigDecimal.ZERO);
    String prefix = nature.toLowerCase(Locale.ROOT);
    var rate = row(rows, prefix + "_wastage_percent");
    assertThat(rate.days()).hasSize(7);
    assertThat(rate.days().getFirst()).isNull();
    assertThat(rate.days().subList(1, 7))
        .allSatisfy(value -> assertThat(value).isEqualByComparingTo("0.1"));
    // All seven waste values count; the zero-sales date contributes zero to the denominator.
    assertThat(rate.total()).isEqualByComparingTo("0.11666667");
    assertThat(rate.status()).isEqualTo("QUALIFIED");
    assertThat(row(rows, prefix + "_wastage").days()).containsOnly(BigDecimal.ONE);
    assertThat(row(rows, prefix + "_wastage").total()).isEqualByComparingTo("7");
  }

  @ParameterizedTest
  @ValueSource(strings = {"FOOD", "BEVERAGE"})
  void negativeSalesDayWithholdsWeeklyWastageRateDespitePositiveWeeklySales(String nature) {
    var rows = wastageWithFirstDaySales(nature, BigDecimal.TEN.negate());
    String prefix = nature.toLowerCase(Locale.ROOT);
    var rate = row(rows, prefix + "_wastage_percent");
    assertThat(rate.days()).hasSize(7);
    assertThat(rate.days().getFirst()).isNull();
    assertThat(rate.days().subList(1, 7))
        .allSatisfy(value -> assertThat(value).isEqualByComparingTo("0.1"));
    // Weekly sales are -10 + 6 * 10 = 50; a positive sum cannot override a negative date.
    assertThat(rate.total()).isNull();
    assertThat(rate.status()).isEqualTo("UNAVAILABLE");
    assertThat(row(rows, prefix + "_wastage").days()).containsOnly(BigDecimal.ONE);
    assertThat(row(rows, prefix + "_wastage").total()).isEqualByComparingTo("7");
  }

  private static List<WeeklyReport.Row> wastageWithFirstDaySales(
      String nature, BigDecimal firstDaySales) {
    var start = LocalDate.parse("2026-10-26");
    var dates = start.datesUntil(start.plusDays(7)).toList();
    var sales =
        dates.stream()
            .map(
                date -> {
                  var amount = date.equals(start) ? firstDaySales : BigDecimal.TEN;
                  return new Sale(date, nature, amount, amount, "CTB", nature);
                })
            .toList();
    var waste =
        dates.stream().map(date -> new Waste(date, "KO", 1, 1, BigDecimal.ONE, nature)).toList();
    return WeeklyReportInventory.calculate(dates, waste, sales, Map.of(), null);
  }

  private static WeeklyReport.Row row(List<WeeklyReport.Row> rows, String id) {
    return rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
  }
}
