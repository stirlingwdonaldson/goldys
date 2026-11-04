package com.goldys.platform.application;

import com.goldys.platform.semantic.ClassifiedSalesTotals;
import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** Keeps the product allocation and the independently resolved headline accountable. */
final class WeeklyReportSales {
  private WeeklyReportSales() {}

  /** Calculates complete sales rows without requiring the report's placeholder rows or layout. */
  static List<WeeklyReport.Row> calculate(
      List<LocalDate> dates, List<Sale> sales, Map<LocalDate, BigDecimal> headlineNetSales) {
    List<ClassifiedSalesTotals> daily =
        dates.stream()
            .map(
                d ->
                    ClassifiedSalesTotals.summarize(
                        sales.stream().filter(s -> s.date().equals(d)).toList()))
            .toList();
    ClassifiedSalesTotals week = ClassifiedSalesTotals.summarize(sales);
    List<String> notes =
        new ArrayList<>(
            List.of(
                "Selected product/day source amounts include GST. Ex-GST = observed amount minus observed tax, only when source gross and quantity exactly match the selected resolved product/day. No inferred GST, extra discount, refund or surcharge adjustment.",
                "Reviewed CTB_PRODUCT site/code policies govern fully matched CTB observations; partial, mixed and UNKNOWN policies hold the alias. Otherwise reviewed PRODUCT_NAME mappings apply on each sale date; browsing categories, cafe identity and lifecycle do not allocate revenue. Paid modifiers follow their reviewed mapping; mixed packages remain unknown until reviewed.",
                "Food/beverage require 95% of absolute known eligible ex-GST product amounts classified on each date; OTHER is outside this denominator. Missing tax or unresolved amounts withhold that date. Week totals are known subtotals of usable dates.",
                "Observed product/day rows: "
                    + week.lines()
                    + "; tax basis unavailable: "
                    + week.missingTax()
                    + "; unresolved gross: "
                    + week.unresolved()
                    + ". Missing records and absent dates are not zero.",
                "Product sources: "
                    + sales.stream()
                        .map(s -> Objects.toString(s.source(), "UNRESOLVED"))
                        .distinct()
                        .sorted()
                        .toList()
                    + ". Product totals can differ from the independently resolved headline; the residual is not allocated."));
    List<LocalDate> withheld = new ArrayList<>();
    for (int i = 0; i < dates.size(); i++)
      if (daily.get(i).food() == null) withheld.add(dates.get(i));
    notes.add("Food/beverage unavailable dates: " + withheld);
    List<WeeklyReport.Row> rows = new ArrayList<>();
    rows.add(
        row(
            "food_sales",
            "Food sales ex GST",
            "AUD",
            daily,
            ClassifiedSalesTotals::food,
            null,
            notes));
    rows.add(
        row(
            "beverage_sales",
            "Beverage sales ex GST",
            "AUD",
            daily,
            ClassifiedSalesTotals::beverage,
            null,
            notes));
    rows.add(
        row(
            "known_food_sales",
            "Reviewed food product sales ex GST (known subtotal)",
            "AUD",
            daily,
            ClassifiedSalesTotals::knownFood,
            null,
            notes));
    rows.add(
        row(
            "known_beverage_sales",
            "Reviewed beverage product sales ex GST (known subtotal)",
            "AUD",
            daily,
            ClassifiedSalesTotals::knownBeverage,
            null,
            notes));
    rows.add(
        row(
            "other_sales",
            "Other product sales ex GST",
            "AUD",
            daily,
            ClassifiedSalesTotals::other,
            null,
            notes));
    rows.add(
        row(
            "unclassified_sales",
            "Unclassified product sales ex GST (known basis)",
            "AUD",
            daily,
            ClassifiedSalesTotals::unknown,
            null,
            notes));
    rows.add(
        row(
            "product_net_sales",
            "Known product sales ex GST",
            "AUD",
            daily,
            ClassifiedSalesTotals::net,
            null,
            notes));
    rows.add(
        row(
            "product_gross_sales",
            "Known product sales incl GST",
            "AUD",
            daily,
            ClassifiedSalesTotals::gross,
            null,
            notes));
    rows.add(
        row(
            "sales_classified_coverage",
            "Food/beverage classification coverage (absolute eligible value)",
            "PERCENT",
            daily,
            ClassifiedSalesTotals::coverage,
            week.coverage(),
            notes));
    rows.add(
        row(
            "sales_missing_tax_rows",
            "Product/day rows without tax basis",
            "count",
            daily,
            s -> s.lines() == 0 ? null : BigDecimal.valueOf(s.missingTax()),
            null,
            notes));
    rows.add(
        row(
            "sales_unresolved_rows",
            "Unresolved product/day amounts",
            "count",
            daily,
            s -> s.lines() == 0 ? null : BigDecimal.valueOf(s.unresolved()),
            null,
            notes));
    List<BigDecimal> residuals = new ArrayList<>();
    for (int i = 0; i < dates.size(); i++) {
      var d = daily.get(i);
      var headline = headlineNetSales.get(dates.get(i));
      residuals.add(
          d.net() == null || d.missingTax() > 0 || d.unresolved() > 0 || headline == null
              ? null
              : headline.subtract(d.net()));
    }
    rows.add(
        new WeeklyReport.Row(
            "sales_reconciliation_difference",
            "Sales",
            "Headline minus product sales ex GST",
            "AUD",
            residuals,
            residuals.stream().filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null),
            residuals.stream().anyMatch(Objects::nonNull) ? "QUALIFIED" : "UNAVAILABLE",
            List.copyOf(notes)));
    return List.copyOf(rows);
  }

  private static WeeklyReport.Row row(
      String id,
      String label,
      String unit,
      List<ClassifiedSalesTotals> days,
      Function<ClassifiedSalesTotals, BigDecimal> pick,
      BigDecimal total,
      List<String> notes) {
    var values = days.stream().map(pick).toList();
    if (total == null && !unit.equals("PERCENT"))
      total = values.stream().filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
    return new WeeklyReport.Row(
        id,
        "Sales",
        label,
        unit,
        values,
        total,
        total == null ? "UNAVAILABLE" : "QUALIFIED",
        List.copyOf(notes));
  }
}
