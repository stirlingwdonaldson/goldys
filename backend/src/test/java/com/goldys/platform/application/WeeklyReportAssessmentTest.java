package com.goldys.platform.application;

import static com.goldys.platform.application.WeeklyReport.AssessmentOutcome.*;
import static com.goldys.platform.application.WeeklyReport.AssessmentReason.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WeeklyReportAssessmentTest {
  private static final LocalDate START = LocalDate.parse("2026-10-26");

  @Test
  void partialPositiveDifferenceRetainsValuesButCannotGetAFavourableVerdict() {
    var rows = sales("100", "110", "10", "0.1");
    var assessments = assess(rows, targets("sales_target", "100"), Set.of());
    for (String id : List.of("net_sales", "sales_difference", "sales_difference_percent")) {
      assertThat(assessments.get(id))
          .isEqualTo(new WeeklyReport.Assessment(WITHHELD, INCOMPLETE_EVIDENCE));
    }
    assertThat(rows.get(2).total()).isEqualByComparingTo("10");
    assertThat(rows.get(1).total()).isEqualByComparingTo("110");
  }

  @Test
  void inclusiveSalesUsesReportedDecimalDifferenceAndIndependentPercentageAvailability() {
    var rows = sales("0", "0", "0", null);
    var assessments = assess(rows, targets("sales_target", "0"), Set.of("net_sales"));
    assertThat(assessments.get("net_sales").outcome()).isEqualTo(MEETS_TARGET);
    assertThat(assessments.get("sales_difference").outcome()).isEqualTo(MEETS_TARGET);
    assertThat(assessments.get("sales_difference_percent").outcome()).isEqualTo(UNAVAILABLE);
    // The reported difference is the financial authority; do not subtract rounded display values.
    rows = sales("100", "100", "-0.00000001", "-0.0000000001");
    assertThat(
            assess(rows, targets("sales_target", "100"), Set.of("net_sales"))
                .get("net_sales")
                .outcome())
        .isEqualTo(BELOW_TARGET);
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "future", "different-value"})
  void rejectsMissingOrIneffectiveTargetSettings(String problem) {
    var targets =
        problem.equals("missing")
            ? List.<WeeklyReportTargets.Target>of()
            : List.of(
                new WeeklyReportTargets.Target(
                    "sales_target",
                    new BigDecimal(problem.equals("different-value") ? "99" : "100"),
                    problem.equals("future") ? START.plusWeeks(1) : START,
                    "test"));
    assertThat(
            assess(sales("100", "110", "10", "0.1"), targets, Set.of("net_sales"))
                .get("net_sales")
                .reason())
        .isEqualTo(NO_EFFECTIVE_TARGET);
  }

  @Test
  void checksUnitsAndRequiresTheReportedCurrencyDifference() {
    var rows = new ArrayList<>(sales("100", "110", "10", "0.1"));
    rows.set(0, row("sales_target", "PERCENT", "100"));
    assertThat(
            assess(rows, targets("sales_target", "100"), Set.of("net_sales"))
                .get("net_sales")
                .reason())
        .isEqualTo(BASIS_MISMATCH);
    rows.set(0, row("sales_target", "AUD", "100"));
    rows.remove(2);
    assertThat(
            assess(rows, targets("sales_target", "100"), Set.of("net_sales"))
                .get("net_sales")
                .reason())
        .isEqualTo(COMPARISON_UNAVAILABLE);
  }

  @Test
  void comparesSupportedMaximumWithoutRequiringDailyRatioCells() {
    var rows =
        List.of(
            row("food_cogs_percent", "PERCENT", "0.25"),
            row("food_cogs_target_percent", "PERCENT", "0.25"));
    assertThat(
            assess(rows, targets("food_cogs_target_percent", "0.25"), Set.of("food_cogs_percent"))
                .get("food_cogs_percent")
                .outcome())
        .isEqualTo(MEETS_TARGET);
    assertThat(
            assess(rows, targets("food_cogs_target_percent", "0.20"), Set.of("food_cogs_percent"))
                .get("food_cogs_percent")
                .reason())
        .isEqualTo(NO_EFFECTIVE_TARGET);
    var above = List.of(row("food_cogs_percent", "PERCENT", "0.25000001"), rows.get(1));
    assertThat(
            assess(above, targets("food_cogs_target_percent", "0.25"), Set.of("food_cogs_percent"))
                .get("food_cogs_percent")
                .outcome())
        .isEqualTo(ABOVE_LIMIT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.0025", "0.0025", "0.00250001"})
  void varianceBoundsAreInclusiveWhenEvidenceIsExplicitlySupported(String actual) {
    var rows =
        List.of(
            row("food_variance_percent", "PERCENT", actual),
            row("food_variance_target_min", "PERCENT", "-0.0025"),
            row("food_variance_target_max", "PERCENT", "0.0025"));
    var targets = new ArrayList<>(targets("food_variance_target_min", "-0.0025"));
    targets.addAll(targets("food_variance_target_max", "0.0025"));
    assertThat(
            assess(rows, targets, Set.of("food_variance_percent"))
                .get("food_variance_percent")
                .outcome())
        .isEqualTo(actual.equals("0.00250001") ? OUTSIDE_RANGE : MEETS_TARGET);
    assertThat(assess(rows, targets, Set.of()).get("food_variance_percent").reason())
        .isEqualTo(INCOMPLETE_EVIDENCE);
  }

  @Test
  void unknownMeasuresHaveNoInventedThresholdAndPlaceholdersRemainUnavailable() {
    var rows =
        List.of(
            row("review_average", "STARS", "4.5"),
            row("cash_variance_percent", "PERCENT", null),
            row("food_variance_percent", "PERCENT", null),
            row("new_measure", "AUD", "100"));
    var assessments = assess(rows, List.of(), Set.of());
    assertThat(assessments.get("review_average").outcome()).isEqualTo(SOURCE_QUALIFIED);
    assertThat(assessments.get("new_measure").outcome()).isEqualTo(SOURCE_QUALIFIED);
    assertThat(assessments.get("cash_variance_percent").outcome()).isEqualTo(UNAVAILABLE);
    assertThat(assessments.get("food_variance_percent").outcome()).isEqualTo(UNAVAILABLE);
  }

  private static Map<String, WeeklyReport.Assessment> assess(
      List<WeeklyReport.Row> rows,
      List<WeeklyReportTargets.Target> targets,
      Set<String> supported) {
    return WeeklyReportAssessment.assess(START, rows, targets, supported);
  }

  private static List<WeeklyReport.Row> sales(
      String target, String actual, String difference, String percent) {
    return List.of(
        row("sales_target", "AUD", target),
        row("net_sales", "AUD", actual),
        row("sales_difference", "AUD", difference),
        row("sales_difference_percent", "PERCENT", percent));
  }

  private static List<WeeklyReportTargets.Target> targets(String id, String value) {
    return List.of(new WeeklyReportTargets.Target(id, new BigDecimal(value), START, "test"));
  }

  private static WeeklyReport.Row row(String id, String unit, String value) {
    return new WeeklyReport.Row(
        id,
        "Test",
        id,
        unit,
        Collections.nCopies(7, null),
        value == null ? null : new BigDecimal(value),
        value == null ? "UNAVAILABLE" : "QUALIFIED",
        List.of("Qualified test evidence"));
  }
}
