package com.goldys.platform.application;

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

  static List<WeeklyReport.Row> fill(
      List<WeeklyReport.Row> original, List<LocalDate> dates, List<Waste> observations) {
    var summary = summarize(observations);
    var daily =
        dates.stream()
            .map(d -> summarize(observations.stream().filter(w -> w.date().equals(d)).toList()))
            .toList();
    List<WeeklyReport.Row> rows = new ArrayList<>();
    WeeklyReport.Row sales =
        original.stream().filter(r -> r.id().equals("net_sales")).findFirst().orElseThrow();
    List<String> notes =
        List.of(
            "Recorded CTB wastage includes every reason and No reason recorded; no missing reasons are inferred.",
            "Known recorded value; "
                + summary.valuedLines()
                + " of "
                + summary.lines()
                + " lines have a value. Receipt completeness and tax basis are unverified.",
            "Percent of sales uses resolved sales ex GST; wastage cost tax basis is unverified. Missing dates are not zero.");
    for (var row : original) {
      if (row.id().equals("total_wastage"))
        rows.add(
            row(
                row.id(),
                row.label(),
                "AUD",
                daily.stream().map(WeeklyReport.Wastage::value).toList(),
                summary.value(),
                notes));
      else if (row.id().equals("total_wastage_percent")) {
        List<BigDecimal> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(ratio(daily.get(i).value(), sales.days().get(i)));
        rows.add(
            row(
                row.id(),
                row.label(),
                "PERCENT",
                days,
                ratio(summary.value(), sales.total()),
                notes));
      } else rows.add(row);
    }
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
    fillClassified(rows, original, dates, observations);
    return List.copyOf(rows);
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
      List<WeeklyReport.Row> original,
      List<LocalDate> dates,
      List<Waste> observations) {
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
      int index = -1;
      for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(id)) index = i;
      if (index < 0) rows.add(valueRow);
      else rows.set(index, valueRow);
      if (!nature.equals("FOOD") && !nature.equals("BEVERAGE")) continue;
      var sales =
          original.stream().filter(r -> r.id().equals(prefix + "_sales")).findFirst().orElseThrow();
      List<BigDecimal> rates = new ArrayList<>();
      for (int i = 0; i < dates.size(); i++)
        rates.add(
            daily.get(i).stream()
                        .anyMatch(w -> nature.equals(w.nature()) && w.valuedLines() < w.lines())
                    || sales.days().get(i) == null
                    || sales.days().get(i).signum() <= 0
                ? null
                : ratio(values.get(i), sales.days().get(i)));
      boolean missingCost =
          observations.stream()
              .anyMatch(w -> nature.equals(w.nature()) && w.valuedLines() < w.lines());
      BigDecimal rate =
          missingCost
                  || sales.days().stream().anyMatch(v -> v == null || v.signum() < 0)
                  || sales.total() == null
                  || sales.total().signum() <= 0
              ? null
              : ratio(total, sales.total());
      for (int i = 0; i < rows.size(); i++)
        if (rows.get(i).id().equals(id + "_percent"))
          rows.set(
              i,
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
