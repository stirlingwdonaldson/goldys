package com.goldys.platform.application;

import static com.goldys.platform.application.WeeklyReport.AssessmentOutcome.*;
import static com.goldys.platform.application.WeeklyReport.AssessmentReason.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** One comparison policy for every presentation of the authorised weekly snapshot. */
final class WeeklyReportAssessment {
  private WeeklyReportAssessment() {}

  private record Rule(String actual, String min, String max, String difference) {}

  private static final List<Rule> RULES =
      List.of(
          new Rule("net_sales", "sales_target", null, "sales_difference"),
          new Rule("food_sales", "food_target", null, "food_difference"),
          new Rule("beverage_sales", "beverage_target", null, "beverage_difference"),
          new Rule("total_wastage_percent", null, "total_wastage_target_percent", null),
          new Rule("food_wastage_percent", null, "food_wastage_target_percent", null),
          new Rule("beverage_wastage_percent", null, "beverage_wastage_target_percent", null),
          new Rule("food_cogs_percent", null, "food_cogs_target_percent", null),
          new Rule("beverage_cogs_percent", null, "beverage_cogs_target_percent", null),
          new Rule(
              "food_variance_percent",
              "food_variance_target_min",
              "food_variance_target_max",
              null),
          new Rule(
              "beverage_variance_percent",
              "beverage_variance_target_min",
              "beverage_variance_target_max",
              null),
          new Rule(
              "cash_variance_percent",
              "cash_variance_target_min",
              "cash_variance_target_max",
              null));

  static Map<String, WeeklyReport.Assessment> assess(
      LocalDate start,
      List<WeeklyReport.Row> rows,
      List<WeeklyReportTargets.Target> targets,
      Set<String> supported) {
    var byId = rows.stream().collect(Collectors.toMap(WeeklyReport.Row::id, Function.identity()));
    Map<String, WeeklyReport.Assessment> results = new LinkedHashMap<>();
    for (var row : rows) {
      results.put(
          row.id(),
          result(
              !available(row)
                  ? UNAVAILABLE
                  : WeeklyReportTargets.IDS.contains(row.id()) ? TARGET : SOURCE_QUALIFIED));
    }
    for (var rule : RULES) {
      if (!byId.containsKey(rule.actual())) continue;
      var assessment = compare(start, rule, byId, targets, supported);
      results.put(rule.actual(), assessment);
      if (rule.difference() == null) continue;
      for (String id : List.of(rule.difference(), rule.difference() + "_percent")) {
        if (byId.containsKey(id)) {
          results.put(id, available(byId.get(id)) ? assessment : result(UNAVAILABLE));
        }
      }
    }
    return Collections.unmodifiableMap(results);
  }

  private static WeeklyReport.Assessment compare(
      LocalDate start,
      Rule rule,
      Map<String, WeeklyReport.Row> rows,
      List<WeeklyReportTargets.Target> targets,
      Set<String> supported) {
    var actual = rows.get(rule.actual());
    if (!available(actual)) return result(UNAVAILABLE);
    for (String id : Arrays.asList(rule.min(), rule.max())) {
      if (id == null) continue;
      var target = rows.get(id);
      if (!available(target)
          || targets.stream()
              .noneMatch(
                  t ->
                      t.id().equals(id)
                          && !t.effectiveFrom().isAfter(start)
                          && t.value().compareTo(target.total()) == 0)) {
        return withheld(NO_EFFECTIVE_TARGET);
      }
      if (!actual.unit().equals(target.unit())) return withheld(BASIS_MISMATCH);
    }
    if (!supported.contains(rule.actual())) return withheld(INCOMPLETE_EVIDENCE);
    if (rule.difference() != null) {
      var difference = rows.get(rule.difference());
      if (!available(difference) || !actual.unit().equals(difference.unit())) {
        return withheld(COMPARISON_UNAVAILABLE);
      }
      // Use the reported decimal difference, never a separately rounded subtraction.
      return result(difference.total().signum() >= 0 ? MEETS_TARGET : BELOW_TARGET);
    }
    BigDecimal min = rule.min() == null ? null : rows.get(rule.min()).total();
    BigDecimal max = rule.max() == null ? null : rows.get(rule.max()).total();
    if (min != null && max != null && min.compareTo(max) > 0) {
      return withheld(COMPARISON_UNAVAILABLE);
    }
    boolean below = min != null && actual.total().compareTo(min) < 0;
    boolean above = max != null && actual.total().compareTo(max) > 0;
    return result(!below && !above ? MEETS_TARGET : min != null ? OUTSIDE_RANGE : ABOVE_LIMIT);
  }

  private static boolean available(WeeklyReport.Row row) {
    return row != null && "QUALIFIED".equals(row.status()) && row.total() != null;
  }

  private static WeeklyReport.Assessment result(WeeklyReport.AssessmentOutcome outcome) {
    return new WeeklyReport.Assessment(outcome, null);
  }

  private static WeeklyReport.Assessment withheld(WeeklyReport.AssessmentReason reason) {
    return new WeeklyReport.Assessment(WITHHELD, reason);
  }
}
