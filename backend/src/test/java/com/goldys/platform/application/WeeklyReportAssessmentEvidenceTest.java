package com.goldys.platform.application;

import static com.goldys.platform.application.WeeklyReport.AssessmentOutcome.*;
import static com.goldys.platform.application.WeeklyReport.AssessmentReason.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import com.goldys.platform.semantic.WeeklyInventoryQuery.Waste;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises evidence through calculations into the same assessment interface used by reports. */
class WeeklyReportAssessmentEvidenceTest {
  private static final LocalDate START = LocalDate.parse("2026-10-26");
  private static final List<LocalDate> DATES = START.datesUntil(START.plusDays(7)).toList();

  @Test
  void knownZeroSalesDayWithUndefinedDailyRatiosStillSupportsWeeklyVerdicts() {
    var calculation = calculate(sales(), waste(), headline(), true);
    for (String id :
        List.of("total_wastage_percent", "food_wastage_percent", "food_cogs_percent")) {
      var row = row(calculation, id);
      assertThat(row.days().getFirst()).isNull();
      assertThat(row.total()).isNotNull();
      assertThat(assess(calculation, id).outcome()).isEqualTo(MEETS_TARGET);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing-date", "unvalued-line"})
  void numericWastageSubtotalDoesNotEstablishComparisonSupport(String problem) {
    var waste = new ArrayList<>(waste());
    if (problem.equals("missing-date")) waste.removeFirst();
    else waste.set(0, new Waste(START, "KO", 2, 1, BigDecimal.ONE, "FOOD"));
    var calculation = calculate(sales(), waste, headline(), true);
    assertThat(row(calculation, "total_wastage_percent").total()).isNotNull();
    var assessment = assess(calculation, "total_wastage_percent");
    assertThat(assessment.outcome()).isEqualTo(WITHHELD);
    assertThat(assessment.reason()).isEqualTo(INCOMPLETE_EVIDENCE);
    assertThat(calculation.supported()).doesNotContain("food_wastage_percent");
  }

  @Test
  void unknownWasteCannotGiveCategoryVerdictButRecordedTotalRemainsComparable() {
    var waste = new ArrayList<>(waste());
    waste.add(new Waste(START, "KO", 1, 1, BigDecimal.ONE, "UNKNOWN"));
    var calculation = calculate(sales(), waste, headline(), true);
    assertThat(row(calculation, "food_wastage_percent").total()).isNotNull();
    assertThat(assess(calculation, "food_wastage_percent").reason()).isEqualTo(INCOMPLETE_EVIDENCE);
    assertThat(assess(calculation, "total_wastage_percent").outcome()).isEqualTo(MEETS_TARGET);
  }

  @Test
  void missingSalesDateWithholdsSalesAndRatioComparisonsWithoutErasingSubtotals() {
    var sales = new ArrayList<>(sales());
    sales.removeFirst();
    var headline = new HashMap<>(headline());
    headline.remove(START);
    var calculation = calculate(sales, waste(), headline, true);
    assertThat(row(calculation, "food_sales").total()).isNotNull();
    assertThat(calculation.supported())
        .doesNotContain(
            "net_sales",
            "food_sales",
            "food_cogs_percent",
            "food_wastage_percent",
            "total_wastage_percent");
  }

  @Test
  void withdrawnCostBasisAndMissingCostCoverageNeverProduceACostVerdict() {
    assertThat(
            assess(calculate(sales(), waste(), headline(), false), "food_cogs_percent").outcome())
        .isEqualTo(UNAVAILABLE);
    var sales = new ArrayList<>(sales());
    sales.set(
        0,
        new Sale(
            START, "Food", BigDecimal.ZERO, BigDecimal.ZERO, "CTB", "FOOD", BigDecimal.ONE, 2, 1));
    var calculation = calculate(sales, waste(), headline(), true);
    assertThat(calculation.supported()).doesNotContain("food_cogs_percent");
    assertThat(row(calculation, "food_known_theoretical_cost").total()).isNotNull();
  }

  private static WeeklyReport.Assessment assess(
      WeeklyReportCalculation.Result calculation, String id) {
    var rows = new ArrayList<>(calculation.rows());
    String targetId = id.replace("_percent", "_target_percent");
    rows.add(
        new WeeklyReport.Row(
            targetId,
            "Test",
            "Target",
            "PERCENT",
            Collections.nCopies(7, null),
            BigDecimal.ONE,
            "QUALIFIED",
            List.of()));
    var targets = List.of(new WeeklyReportTargets.Target(targetId, BigDecimal.ONE, START, "test"));
    return WeeklyReportAssessment.assess(START, rows, targets, calculation.supported()).get(id);
  }

  private static WeeklyReport.Row row(WeeklyReportCalculation.Result result, String id) {
    return result.rows().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
  }

  private static WeeklyReportCalculation.Result calculate(
      List<Sale> sales, List<Waste> waste, Map<LocalDate, BigDecimal> headline, boolean costBasis) {
    return WeeklyReportCalculation.calculate(
        new WeeklyReportCalculation.Context(DATES, headline, new BigDecimal("600"), costBasis),
        sales,
        waste,
        List.of());
  }

  private static Map<LocalDate, BigDecimal> headline() {
    Map<LocalDate, BigDecimal> values = new HashMap<>();
    for (var date : DATES)
      values.put(date, date.equals(START) ? BigDecimal.ZERO : new BigDecimal("100"));
    return values;
  }

  private static List<Sale> sales() {
    return DATES.stream()
        .map(
            d -> {
              var net = d.equals(START) ? BigDecimal.ZERO : new BigDecimal("100");
              return new Sale(d, "Food", net, net, "CTB", "FOOD", BigDecimal.TEN, 1, 1);
            })
        .toList();
  }

  private static List<Waste> waste() {
    return DATES.stream().map(d -> new Waste(d, "KO", 1, 1, BigDecimal.ONE, "FOOD")).toList();
  }
}
