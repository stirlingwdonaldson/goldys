package com.goldys.platform.application;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import com.goldys.platform.semantic.WeeklyInventoryQuery.Waste;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure weekly calculations; report layout and data access stay with their callers. */
final class WeeklyReportCalculation {
  private WeeklyReportCalculation() {}

  record Context(
      List<LocalDate> dates,
      Map<LocalDate, BigDecimal> headlineNetSales,
      BigDecimal headlineWeeklyNetSales,
      boolean costsExcludeGst) {}

  record Result(
      List<WeeklyReport.Row> rows,
      List<WeeklyReportTargetComparisons.Comparison> comparisons,
      WeeklyReport.Wastage wastage,
      Set<String> supported) {}

  /** Internal evidence: recorded operands support comparison, not certified source completeness. */
  record Rows(List<WeeklyReport.Row> rows, Set<String> supported) {
    Rows {
      rows = List.copyOf(rows);
      supported = Set.copyOf(supported);
    }
  }

  static Result calculate(
      Context context,
      List<Sale> sales,
      List<Waste> waste,
      List<WeeklyReportTargets.Target> targets) {
    // Keep additional report rows in their established sales, wastage, then cost order.
    var salesRows = WeeklyReportSales.calculate(context.dates(), sales, context.headlineNetSales());
    var rows = new ArrayList<>(salesRows.rows());
    var supported = new HashSet<>(salesRows.supported());
    if (context.dates().size() == 7
        && context.dates().stream().allMatch(d -> context.headlineNetSales().get(d) != null)) {
      supported.add("net_sales");
    }
    var inventoryRows =
        WeeklyReportInventory.calculate(
            context.dates(),
            waste,
            sales,
            context.headlineNetSales(),
            context.headlineWeeklyNetSales());
    var costRows = WeeklyReportCogs.calculate(context.dates(), sales, context.costsExcludeGst());
    rows.addAll(inventoryRows.rows());
    rows.addAll(costRows.rows());
    supported.addAll(inventoryRows.supported());
    supported.addAll(costRows.supported());
    return new Result(
        List.copyOf(rows),
        WeeklyReportTargetComparisons.calculate(
            context.dates(), sales, context.headlineWeeklyNetSales(), targets),
        WeeklyReportInventory.summarize(waste),
        Set.copyOf(supported));
  }
}
