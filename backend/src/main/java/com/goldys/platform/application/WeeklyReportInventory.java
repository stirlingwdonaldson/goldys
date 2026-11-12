package com.goldys.platform.application;

import com.goldys.platform.semantic.ClassifiedSalesTotals;
import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import com.goldys.platform.semantic.WeeklyInventoryQuery.Waste;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Missing reasons stay in the total; coverage describes recorded observations only. */
final class WeeklyReportInventory {
  private WeeklyReportInventory() {}

  static BigDecimal ratio(BigDecimal part, BigDecimal total) {
    return part == null || total == null || total.signum() == 0
        ? null
        : part.divide(total, 8, RoundingMode.HALF_UP);
  }

  static WeeklyReport.Wastage summarize(List<Waste> rows) {
    long lines = rows.stream().mapToLong(Waste::lines).sum();
    long valued = rows.stream().mapToLong(Waste::valuedLines).sum();
    BigDecimal value = sum(rows);
    List<Waste> reasoned =
        rows.stream().filter(w -> w.reason() != null && !w.reason().isBlank()).toList();
    Map<String, List<Waste>> groups = new TreeMap<>();
    for (Waste row : rows)
      groups.computeIfAbsent(reason(row.reason()), k -> new ArrayList<>()).add(row);
    List<WeeklyReport.WasteReason> reasons = new ArrayList<>();
    for (var group : groups.entrySet()) {
      List<Waste> items = group.getValue();
      reasons.add(
          new WeeklyReport.WasteReason(
              group.getKey(),
              items.stream()
                  .map(Waste::reason)
                  .filter(Objects::nonNull)
                  .distinct()
                  .sorted()
                  .toList(),
              items.stream().mapToLong(Waste::lines).sum(),
              items.stream().mapToLong(Waste::valuedLines).sum(),
              sum(items),
              ratio(sum(items), value)));
    }
    return new WeeklyReport.Wastage(
        lines,
        valued,
        value,
        ratio(
            BigDecimal.valueOf(reasoned.stream().mapToLong(Waste::lines).sum()),
            BigDecimal.valueOf(lines)),
        ratio(sum(reasoned) == null ? BigDecimal.ZERO : sum(reasoned), value),
        List.copyOf(reasons));
  }

  private static BigDecimal sum(List<Waste> rows) {
    return rows.stream()
        .map(Waste::value)
        .filter(Objects::nonNull)
        .reduce(BigDecimal::add)
        .orElse(null);
  }

  private static String reason(String text) {
    if (text == null || text.isBlank()) return "No reason recorded";
    String normalized = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    return normalized.replace(" ", "").equals("knockoff") ? "knock off" : normalized;
  }

  /** Calculates wastage without report placeholders or previously assembled sales rows. */
  static WeeklyReportCalculation.Rows calculate(
      List<LocalDate> dates,
      List<Waste> observations,
      List<Sale> sales,
      Map<LocalDate, BigDecimal> headlineNetSales,
      BigDecimal headlineWeeklyNetSales) {
    var summary = summarize(observations);
    var daily =
        dates.stream()
            .map(d -> summarize(observations.stream().filter(w -> w.date().equals(d)).toList()))
            .toList();
    List<WeeklyReport.Row> rows = new ArrayList<>();
    Set<String> supported = new HashSet<>();
    // Recorded operands, not ratio cells: a known zero-sales date has no daily percentage.
    if (dates.size() == 7
        && daily.stream().allMatch(d -> d.value() != null && d.lines() == d.valuedLines())
        && dates.stream()
            .allMatch(d -> headlineNetSales.get(d) != null && headlineNetSales.get(d).signum() >= 0)
        && headlineWeeklyNetSales != null
        && headlineWeeklyNetSales.signum() > 0) {
      supported.add("total_wastage_percent");
    }
    List<String> notes =
        List.of(
            "Recorded CTB wastage includes every reason and No reason recorded; no missing reasons are inferred.",
            "Known recorded value; "
                + summary.valuedLines()
                + " of "
                + summary.lines()
                + " lines have a value. Receipt completeness and tax basis are unverified.",
            "Percent of sales uses resolved sales ex GST; wastage cost tax basis is unverified. Missing dates are not zero.");
    rows.add(
        row(
            "total_wastage",
            "Total recorded wastage",
            "AUD",
            daily.stream().map(WeeklyReport.Wastage::value).toList(),
            summary.value(),
            notes));
    List<BigDecimal> days = new ArrayList<>();
    for (int i = 0; i < dates.size(); i++)
      days.add(ratio(daily.get(i).value(), headlineNetSales.get(dates.get(i))));
    rows.add(
        row(
            "total_wastage_percent",
            "Total wastage %",
            "PERCENT",
            days,
            ratio(summary.value(), headlineWeeklyNetSales),
            notes));
    rows.add(
        row(
            "wastage_reason_coverage_lines",
            "Wastage with reasons (by lines)",
            "PERCENT",
            daily.stream().map(WeeklyReport.Wastage::reasonCoverageLines).toList(),
            summary.reasonCoverageLines(),
            notes));
    rows.add(
        row(
            "wastage_reason_coverage_value",
            "Wastage with reasons (by recorded value)",
            "PERCENT",
            daily.stream().map(WeeklyReport.Wastage::reasonCoverageValue).toList(),
            summary.reasonCoverageValue(),
            notes));
    var dailySales =
        dates.stream()
            .map(
                d ->
                    ClassifiedSalesTotals.summarize(
                        sales.stream().filter(s -> s.date().equals(d)).toList()))
            .toList();
    fillClassified(rows, dailySales, dates, observations, supported);
    return new WeeklyReportCalculation.Rows(rows, supported);
  }

  private static BigDecimal classifiedValue(List<Waste> observations, String nature) {
    if (observations.isEmpty()) return null;
    List<Waste> selected = observations.stream().filter(w -> nature.equals(w.nature())).toList();
    return selected.isEmpty() ? BigDecimal.ZERO : sum(selected);
  }

  private static BigDecimal coverage(List<Waste> observations, boolean value) {
    List<Waste> classified =
        observations.stream().filter(w -> !"UNKNOWN".equals(w.nature())).toList();
    if (value)
      return ratio(sum(classified) == null ? BigDecimal.ZERO : sum(classified), sum(observations));
    return ratio(
        BigDecimal.valueOf(classified.stream().mapToLong(Waste::lines).sum()),
        BigDecimal.valueOf(observations.stream().mapToLong(Waste::lines).sum()));
  }

  private static void fillClassified(
      List<WeeklyReport.Row> rows,
      List<ClassifiedSalesTotals> dailySales,
      List<LocalDate> dates,
      List<Waste> observations,
      Set<String> supported) {
    var daily =
        dates.stream()
            .map(d -> observations.stream().filter(w -> w.date().equals(d)).toList())
            .toList();
    List<String> notes =
        List.of(
            "Recorded CTB quantity × source unit cost; no ml/each/recipe unit conversion. Reviewed exact waste-line mapping takes precedence over CTB recipe ID, including UNKNOWN withdrawals. Sale names never identify inventory.",
            "Includes all recorded reasons, KO and missing reasons; no staff-meal or knock-off exclusions are inferred. Food + beverage + other + unclassified reconcile to known recorded value.",
            "Cost tax basis and source completeness remain unverified; percentages are recorded source-cost / ex-GST sales comparisons, not certified loss rates. Weekly rates require all seven matching sales dates and known costs for included lines.",
            "Known values: "
                + observations.stream().mapToLong(Waste::valuedLines).sum()
                + " of "
                + observations.stream().mapToLong(Waste::lines).sum()
                + " recorded lines. Missing observations are not zero.");
    for (String nature : List.of("FOOD", "BEVERAGE", "OTHER", "UNKNOWN")) {
      String prefix = nature.equals("UNKNOWN") ? "unclassified" : nature.toLowerCase(Locale.ROOT);
      String id = prefix + "_wastage";
      var values = daily.stream().map(d -> classifiedValue(d, nature)).toList();
      var total = classifiedValue(observations, nature);
      var valueRow =
          row(
              id,
              prefix.substring(0, 1).toUpperCase(Locale.ROOT)
                  + prefix.substring(1)
                  + " recorded wastage",
              "AUD",
              values,
              total,
              notes);
      rows.add(valueRow);
      if (!nature.equals("FOOD") && !nature.equals("BEVERAGE")) continue;
      var sales =
          dailySales.stream().map(s -> nature.equals("FOOD") ? s.food() : s.beverage()).toList();
      var salesTotal = sales.stream().filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
      List<BigDecimal> rates = new ArrayList<>();
      for (int i = 0; i < dates.size(); i++)
        rates.add(
            daily.get(i).stream()
                        .anyMatch(w -> nature.equals(w.nature()) && w.valuedLines() < w.lines())
                    || sales.get(i) == null
                    || sales.get(i).signum() <= 0
                ? null
                : ratio(values.get(i), sales.get(i)));
      boolean missingCost =
          observations.stream()
              .anyMatch(w -> nature.equals(w.nature()) && w.valuedLines() < w.lines());
      BigDecimal rate =
          missingCost
                  || sales.stream().anyMatch(v -> v == null || v.signum() < 0)
                  || salesTotal == null
                  || salesTotal.signum() <= 0
              ? null
              : ratio(total, salesTotal);
      // A known subtotal can be displayed even when an absent date or unknown category
      // prevents a verdict. No observations must never become zero wastage.
      if (dates.size() == 7
          && rate != null
          && daily.stream().noneMatch(List::isEmpty)
          && observations.stream().noneMatch(w -> "UNKNOWN".equals(w.nature()))) {
        supported.add(id + "_percent");
      }
      rows.add(
          row(
              id + "_percent",
              valueRow.label() + " / matching sales",
              "PERCENT",
              rates,
              rate,
              notes));
    }
    for (boolean value : List.of(false, true))
      rows.add(
          row(
              "wastage_classified_coverage_" + (value ? "value" : "lines"),
              "Classified wastage (by " + (value ? "known recorded value" : "lines") + ")",
              "PERCENT",
              daily.stream().map(d -> coverage(d, value)).toList(),
              coverage(observations, value),
              notes));
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
        "Wastage",
        label,
        unit,
        days,
        total,
        total == null ? "UNAVAILABLE" : "QUALIFIED",
        notes);
  }
}
