package com.goldys.platform.application;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import com.goldys.platform.semantic.WeeklyInventoryQuery.Waste;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
      WeeklyReport.Wastage wastage) {}

  static Result calculate(
      Context context,
      List<Sale> sales,
      List<Waste> waste,
      List<WeeklyReportTargets.Target> targets) {
    // Keep additional report rows in their established sales, wastage, then cost order.
    var rows =
        new ArrayList<>(
            WeeklyReportSales.calculate(context.dates(), sales, context.headlineNetSales()));
    rows.addAll(
        WeeklyReportInventory.calculate(
            context.dates(),
            waste,
            sales,
            context.headlineNetSales(),
            context.headlineWeeklyNetSales()));
    rows.addAll(WeeklyReportCogs.calculate(context.dates(), sales, context.costsExcludeGst()));
    return new Result(
        List.copyOf(rows),
        WeeklyReportTargetComparisons.calculate(
            context.dates(), sales, context.headlineWeeklyNetSales(), targets),
        WeeklyReportInventory.summarize(waste));
  }
}
