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
    List<WeeklyReport.Row> rows = new ArrayList<>();
    for (String id :
        List.of(
            "food_theoretical_cogs",
            "food_cogs_percent",
            "beverage_theoretical_cogs",
            "beverage_cogs_percent",
            "total_theoretical_cogs",
            "total_theoretical_cogs_percent"))
      rows.add(
          new WeeklyReport.Row(
              id,
              "Cost of goods",
              id,
              "AUD",
              Arrays.asList(null, null, null, null, null, null, null),
              null,
              "UNAVAILABLE",
              List.of()));
    rows.add(
        new WeeklyReport.Row(
            "food_sales",
            "Sales",
            "Food",
            "AUD",
            Collections.nCopies(7, new BigDecimal("100")),
            new BigDecimal("700"),
            "QUALIFIED",
            List.of()));
    rows.add(
        new WeeklyReport.Row(
            "beverage_sales",
            "Sales",
            "Beverage",
            "AUD",
            Collections.nCopies(7, new BigDecimal("50")),
            new BigDecimal("350"),
            "QUALIFIED",
            List.of()));
    var result = new HashMap<String, WeeklyReport.Row>();
    WeeklyReportCogs.fill(rows, dates, sales, tax).forEach(r -> result.put(r.id(), r));
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
