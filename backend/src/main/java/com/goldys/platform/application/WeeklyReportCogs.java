package com.goldys.platform.application;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** CTB's observed sale-report estimates; incomplete cost coverage never becomes complete COGS. */
final class WeeklyReportCogs {
  private WeeklyReportCogs() {}

  private static BigDecimal known(List<Sale> sales) {
    return sales.stream()
        .map(Sale::knownCost)
        .filter(Objects::nonNull)
        .reduce(BigDecimal::add)
        .orElse(null);
  }

  private static BigDecimal coverage(List<Sale> sales) {
    return WeeklyReportInventory.ratio(
        BigDecimal.valueOf(sales.stream().mapToLong(Sale::costedLines).sum()),
        BigDecimal.valueOf(sales.stream().mapToLong(Sale::costLines).sum()));
  }

  private static BigDecimal complete(List<Sale> all, List<Sale> selected) {
    if (selected.isEmpty()
        || all.stream().anyMatch(s -> s.nature().equals("UNKNOWN") || s.net() == null)
        || selected.stream()
            .anyMatch(s -> s.knownCost() == null || s.costLines() != s.costedLines())) return null;
    return known(selected);
  }

  /** Calculates complete theoretical-cost rows without requiring report placeholders or layout. */
  static WeeklyReportCalculation.Rows calculate(
      List<LocalDate> dates, List<Sale> observations, boolean costsExcludeGst) {
    List<WeeklyReport.Row> rows = new ArrayList<>();
    Set<String> supported = new HashSet<>();
    for (String prefix : List.of("food", "beverage", "total")) {
      var selected =
          observations.stream()
              .filter(
                  s ->
                      prefix.equals("total")
                          ? s.nature().equals("FOOD") || s.nature().equals("BEVERAGE")
                          : s.nature().equals(prefix.toUpperCase(Locale.ROOT)))
              .toList();
      var daily =
          dates.stream()
              .map(d -> selected.stream().filter(s -> s.date().equals(d)).toList())
              .toList();
      List<BigDecimal> costs = new ArrayList<>();
      for (int i = 0; i < dates.size(); i++) {
        var date = dates.get(i);
        costs.add(
            complete(
                observations.stream().filter(s -> s.date().equals(date)).toList(), daily.get(i)));
      }
      BigDecimal total = costs.stream().anyMatch(Objects::isNull) ? null : known(selected);
      String label = prefix.substring(0, 1).toUpperCase(Locale.ROOT) + prefix.substring(1);
      List<String> notes =
          List.of(
              "CTB sale-report totalFoodCost, checked against quantitySold × costPerUnit. Zero unit cost with nonzero quantity is unsupported, not free consumption. Selected source, gross, quantity, site and tax evidence must agree.",
              "Source-reported theoretical estimate as observed; historical recipe versions, yields and completeness are unverified. No current recipe substitution, purchase allocation or additional wastage deduction. Total covers food and beverage only.",
              "Known cost for "
                  + selected.stream().mapToLong(Sale::costedLines).sum()
                  + " of "
                  + selected.stream().mapToLong(Sale::costLines).sum()
                  + " reviewed-category observations (an unmatched product/day counts as one unsupported observation); unknown classifications are excluded from this coverage. Full totals require seven supported dates and no unknown classification. Partial known costs are separate subtotals.",
              costsExcludeGst
                  ? "ASSUMED_EX_GST: existing owner recipe-cost assumption; ratios compare matching category sales ex GST."
                  : "UNVERIFIED cost tax basis: COGS ratios withheld.");
      rows.add(
          row(
              prefix + "_theoretical_cogs",
              label + " theoretical COGS (CTB estimate)",
              "AUD",
              costs,
              total,
              notes));
      List<BigDecimal> rates = new ArrayList<>();
      for (int i = 0; i < dates.size(); i++)
        rates.add(rate(costs.get(i), revenue(daily.get(i)), costsExcludeGst));
      String rateId =
          prefix + (prefix.equals("total") ? "_theoretical_cogs_percent" : "_cogs_percent");
      BigDecimal weeklyRate = rate(total, revenue(selected), costsExcludeGst);
      if (dates.size() == 7 && weeklyRate != null) supported.add(rateId);
      rows.add(
          row(
              rateId,
              label + " theoretical COGS / matching sales",
              "PERCENT",
              rates,
              weeklyRate,
              notes));
      rows.add(
          row(
              prefix + "_known_theoretical_cost",
              label + " known CTB theoretical cost subtotal",
              "AUD",
              daily.stream().map(WeeklyReportCogs::known).toList(),
              known(selected),
              notes));
      rows.add(
          row(
              prefix + "_cost_coverage_lines",
              label + " source cost coverage (by observations)",
              "PERCENT",
              daily.stream().map(WeeklyReportCogs::coverage).toList(),
              coverage(selected),
              notes));
    }
    for (String prefix : List.of("food", "beverage"))
      rows.add(
          row(
              prefix + "_cogs_ytd_percent",
              (prefix.equals("food") ? "Food" : "Beverage") + " COGS YTD %",
              "PERCENT",
              Collections.nCopies(dates.size(), null),
              null,
              List.of(
                  "Unavailable: year-to-date cost coverage and year boundary are not established. Weekly estimates do not establish YTD COGS.")));
    return new WeeklyReportCalculation.Rows(rows, supported);
  }

  private static BigDecimal revenue(List<Sale> sales) {
    if (sales.isEmpty() || sales.stream().anyMatch(s -> s.net() == null)) return null;
    return sales.stream().map(Sale::net).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static BigDecimal rate(BigDecimal cost, BigDecimal revenue, boolean costsExcludeGst) {
    return !costsExcludeGst || revenue == null || revenue.signum() <= 0
        ? null
        : WeeklyReportInventory.ratio(cost, revenue);
  }

  private static WeeklyReport.Row row(
      String id,
      String label,
      String unit,
      List<BigDecimal> days,
      BigDecimal total,
      List<String> notes) {
    return new WeeklyReport.Row(
        id,
        "Cost of goods",
        label,
        unit,
        days,
        total,
        total == null && days.stream().allMatch(Objects::isNull) ? "UNAVAILABLE" : "QUALIFIED",
        notes);
  }
}
