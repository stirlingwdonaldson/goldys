package com.goldys.platform.application;

import com.goldys.platform.semantic.ClassifiedSalesTotals;
import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Effective weekly comparisons, independent of report rows and their assembly order. */
final class WeeklyReportTargetComparisons {
  private WeeklyReportTargetComparisons() {}

  record Comparison(String prefix, BigDecimal target, BigDecimal difference, BigDecimal percent) {}

  static List<Comparison> calculate(
      List<LocalDate> dates,
      List<Sale> sales,
      BigDecimal headlineWeeklyNetSales,
      List<WeeklyReportTargets.Target> targets) {
    Map<String, BigDecimal> byId = new HashMap<>();
    for (var target : targets) byId.put(target.id(), target.value());
    var daily =
        dates.stream()
            .map(
                d ->
                    ClassifiedSalesTotals.summarize(
                        sales.stream().filter(s -> s.date().equals(d)).toList()))
            .toList();
    List<Comparison> comparisons = new ArrayList<>();
    for (String prefix : List.of("sales", "food", "beverage")) {
      var target = byId.get(prefix.equals("sales") ? "sales_target" : prefix + "_target");
      // Match reported actuals: classified weekly sales are known subtotals of usable dates.
      var actual =
          prefix.equals("sales")
              ? headlineWeeklyNetSales
              : daily.stream()
                  .map(s -> prefix.equals("food") ? s.food() : s.beverage())
                  .filter(Objects::nonNull)
                  .reduce(BigDecimal::add)
                  .orElse(null);
      if (target == null || actual == null) continue;
      var difference = actual.subtract(target);
      comparisons.add(
          new Comparison(
              prefix, target, difference, WeeklyReportInventory.ratio(difference, target)));
    }
    return List.copyOf(comparisons);
  }
}
