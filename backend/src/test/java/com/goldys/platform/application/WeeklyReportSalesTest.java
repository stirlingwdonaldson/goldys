package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class WeeklyReportSalesTest {
  private final LocalDate monday = LocalDate.parse("2026-10-26");
  private final List<LocalDate> dates = monday.datesUntil(monday.plusDays(7)).toList();

  private Sale sale(String name, String net, String nature) {
    return new Sale(
        monday,
        name,
        new BigDecimal("110"),
        net == null ? null : new BigDecimal(net),
        "CTB",
        nature);
  }

  private List<WeeklyReport.Row> report(List<Sale> sales) {
    return WeeklyReportSales.calculate(dates, sales, Map.of(monday, new BigDecimal("200")));
  }

  private BigDecimal total(List<WeeklyReport.Row> rows, String id) {
    return rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow().total();
  }

  @Test
  void reconcilesSignedAmountsWithoutTaxOrResidualGuessing() {
    var rows =
        report(
            List.of(
                sale("food", "100", "FOOD"),
                sale("refund", "-10", "FOOD"),
                sale("drink", "100", "BEVERAGE"),
                sale("shirt", "20", "OTHER"),
                sale("mixed", "5", "UNKNOWN")));
    assertThat(total(rows, "food_sales")).isEqualByComparingTo("90");
    assertThat(total(rows, "beverage_sales")).isEqualByComparingTo("100");
    assertThat(total(rows, "other_sales")).isEqualByComparingTo("20");
    assertThat(total(rows, "unclassified_sales")).isEqualByComparingTo("5");
    assertThat(total(rows, "product_net_sales")).isEqualByComparingTo("215");
    assertThat(total(rows, "sales_reconciliation_difference")).isEqualByComparingTo("-15");
    assertThat(
            rows.stream()
                .filter(r -> r.id().equals("food_sales"))
                .findFirst()
                .orElseThrow()
                .days()
                .get(1))
        .isNull();
  }

  @Test
  void preservesNinetyFivePercentGateAndVisibleKnownAndUnknownAmounts() {
    var rows = report(List.of(sale("food", "94", "FOOD"), sale("mixed", "6", "UNKNOWN")));
    assertThat(total(rows, "food_sales")).isNull();
    assertThat(total(rows, "known_food_sales")).isEqualByComparingTo("94");
    assertThat(total(rows, "unclassified_sales")).isEqualByComparingTo("6");
    assertThat(total(rows, "sales_classified_coverage")).isEqualByComparingTo("0.94");
    assertThat(
            total(
                report(List.of(sale("food", "95", "FOOD"), sale("mixed", "5", "UNKNOWN"))),
                "food_sales"))
        .isEqualByComparingTo("95");
  }

  @Test
  void missingTaxAndUnresolvedAmountsWithholdEvenApprovedProducts() {
    var rows = report(List.of(sale("food", "100", "FOOD"), sale("drink", null, "BEVERAGE")));
    assertThat(total(rows, "food_sales")).isNull();
    assertThat(total(rows, "sales_missing_tax_rows")).isEqualByComparingTo("1");
    assertThat(total(rows, "sales_reconciliation_difference")).isNull();
    assertThat(total(report(List.of()), "food_sales")).isNull();
  }

  @Test
  void explicitlyObservedZeroSalesIsZeroButCoverageDenominatorIsUnknown() {
    var rows = report(List.of(sale("food", "0", "FOOD")));
    assertThat(total(rows, "food_sales")).isZero();
    assertThat(total(rows, "beverage_sales")).isZero();
    assertThat(total(rows, "sales_classified_coverage")).isNull();
    assertThat(WeeklyReportInventory.ratio(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
  }

  @Test
  void missingHeadlineWithholdsResidualWithoutWithholdingObservedProductSales() {
    var rows = WeeklyReportSales.calculate(dates, List.of(sale("food", "100", "FOOD")), Map.of());
    assertThat(total(rows, "food_sales")).isEqualByComparingTo("100");
    assertThat(total(rows, "sales_reconciliation_difference")).isNull();
  }
}
