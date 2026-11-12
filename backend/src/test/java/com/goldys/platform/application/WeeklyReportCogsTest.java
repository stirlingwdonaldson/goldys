package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class WeeklyReportCogsTest {
  private final List<LocalDate> dates =
      LocalDate.parse("2026-10-26").datesUntil(LocalDate.parse("2026-11-02")).toList();

  private List<Sale> sales() {
    List<Sale> sales = new ArrayList<>();
    for (var date : dates) {
      sales.add(
          new Sale(
              date,
              "food",
              new BigDecimal("110"),
              new BigDecimal("100"),
              "CTB",
              "FOOD",
              new BigDecimal("25"),
              1,
              1));
      sales.add(
          new Sale(
              date,
              "drink",
              new BigDecimal("55"),
              new BigDecimal("50"),
              "CTB",
              "BEVERAGE",
              new BigDecimal("10"),
              1,
              1));
    }
    return sales;
  }

  private Map<String, WeeklyReport.Row> report(List<Sale> sales, boolean tax) {
    var result = new HashMap<String, WeeklyReport.Row>();
    WeeklyReportCogs.calculate(dates, sales, tax).rows().forEach(r -> result.put(r.id(), r));
    return result;
  }

  @Test
  void fullWeekUsesSumOfMatchingCostsAndCategoryRevenue() {
    var rows = report(sales(), true);
    assertThat(rows.get("food_theoretical_cogs").total()).isEqualByComparingTo("175");
    assertThat(rows.get("food_cogs_percent").total()).isEqualByComparingTo("0.25");
    assertThat(rows.get("beverage_cogs_percent").total()).isEqualByComparingTo("0.2");
    assertThat(rows.get("total_theoretical_cogs").total()).isEqualByComparingTo("245");
    assertThat(rows.get("total_theoretical_cogs_percent").total())
        .isEqualByComparingTo("0.23333333");
    // Complete weekly observations do not establish year-to-date cost coverage.
    for (String prefix : List.of("food", "beverage")) {
      var ytd = rows.get(prefix + "_cogs_ytd_percent");
      assertThat(ytd.days()).hasSize(7).containsOnlyNulls();
      assertThat(ytd.total()).isNull();
      assertThat(ytd.status()).isEqualTo("UNAVAILABLE");
      assertThat(ytd.unit()).isEqualTo("PERCENT");
      assertThat(ytd.notes())
          .containsExactly(
              "Unavailable: year-to-date cost coverage and year boundary are not established. Weekly estimates do not establish YTD COGS.");
    }
  }

  @Test
  void missingCostKeepsKnownSubtotalWithoutPublishingIncompleteCogs() {
    var sales = sales();
    sales.add(
        new Sale(
            dates.getFirst(),
            "missing",
            BigDecimal.TEN,
            BigDecimal.TEN,
            "CTB",
            "FOOD",
            null,
            1,
            0));
    var rows = report(sales, true);
    assertThat(rows.get("food_theoretical_cogs").total()).isNull();
    assertThat(rows.get("food_theoretical_cogs").days().getFirst()).isNull();
    assertThat(rows.get("food_known_theoretical_cost").total()).isEqualByComparingTo("175");
    assertThat(rows.get("food_cost_coverage_lines").total()).isEqualByComparingTo("0.875");
    assertThat(rows.get("beverage_theoretical_cogs").total()).isEqualByComparingTo("70");
    assertThat(rows.get("total_theoretical_cogs").total()).isNull();
  }

  @Test
  void unknownClassificationHoldsTotalsEvenForZeroRevenueAndMissingDatesAreNotZero() {
    var sales = sales();
    sales.add(
        new Sale(
            dates.getFirst(),
            "modifier",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "CTB",
            "UNKNOWN",
            null,
            1,
            0));
    var rows = report(sales, true);
    assertThat(rows.get("food_theoretical_cogs").total()).isNull();
    assertThat(rows.get("beverage_theoretical_cogs").days().getFirst()).isNull();
    sales.removeIf(s -> s.date().equals(dates.getLast()));
    rows = report(sales, true);
    assertThat(rows.get("food_theoretical_cogs").days().getLast()).isNull();
    assertThat(rows.get("food_known_theoretical_cost").days().getLast()).isNull();
    assertThat(rows.get("food_cost_coverage_lines").days().getLast()).isNull();
  }

  @Test
  void withdrawingTaxAssumptionWithholdsRatesButRetainsSourceCosts() {
    var rows = report(sales(), false);
    assertThat(rows.get("food_theoretical_cogs").total()).isEqualByComparingTo("175");
    assertThat(rows.get("food_cogs_percent").total()).isNull();
    assertThat(rows.get("food_cogs_percent").days()).containsOnlyNulls();
  }
}
