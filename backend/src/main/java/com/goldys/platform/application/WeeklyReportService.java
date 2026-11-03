package com.goldys.platform.application;

import com.goldys.platform.auth.*;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.semantic.ReviewMetricsQuery;
import com.goldys.platform.semantic.catalog.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyReportService {
  private final MetricQueryService metrics;
  private final MetricCatalog catalog;
  private final ReviewMetricsQuery reviews;
  private final PermissionService permissions;
  private final VenueClock clock;

  public WeeklyReportService(
      MetricQueryService metrics,
      MetricCatalog catalog,
      ReviewMetricsQuery reviews,
      PermissionService permissions,
      VenueClock clock) {
    this.metrics = metrics;
    this.catalog = catalog;
    this.reviews = reviews;
    this.permissions = permissions;
    this.clock = clock;
  }

  private record Field(
      String id, String section, String label, String unit, MetricId metric, String note) {}

  private static Field missing(
      String id, String section, String label, String unit, String reason) {
    return new Field(id, section, label, unit, null, reason);
  }

  private static final String SPLIT =
      "Unavailable pending review of food/beverage classification and ex-GST allocation.";
  private static final String TARGET = "Unavailable: no approved target feed is configured.";
  private static final String STOCK =
      "Unavailable: stocktake, consumption and valuation evidence is not verified.";
  private static final String WASTE =
      "Unavailable: category/reason exclusions and cost basis need verification.";

  private static List<Field> fields() {
    List<Field> fields = new ArrayList<>();
    fields.add(missing("food_sales", "Sales", "Food sales ex GST", "AUD", SPLIT));
    fields.add(missing("food_target", "Sales", "Food sales target", "AUD", TARGET));
    fields.add(missing("beverage_sales", "Sales", "Beverage sales ex GST", "AUD", SPLIT));
    fields.add(missing("beverage_target", "Sales", "Beverage sales target", "AUD", TARGET));
    fields.add(
        new Field(
            "net_sales",
            "Sales",
            "Total sales ex GST",
            "AUD",
            MetricId.SALES_NET,
            "Resolved headline sales. Source-supplied dates are retained; historical sources differ."));
    fields.add(
        new Field("gst", "Sales", "GST", "AUD", MetricId.SALES_GST, "Source-qualified GST."));
    fields.add(
        new Field(
            "gross_sales",
            "Sales",
            "Total sales incl GST",
            "AUD",
            MetricId.SALES_GROSS,
            "Resolved headline sales including GST; scope may exceed food/beverage."));
    fields.add(missing("sales_target", "Sales", "Total sales target", "AUD", TARGET));
    fields.add(missing("sales_difference", "Sales", "Sales versus target", "AUD", TARGET));
    fields.add(
        missing("sales_difference_percent", "Sales", "Sales versus target %", "PERCENT", TARGET));
    fields.add(
        new Field(
            "payments",
            "Cash and takings",
            "Recorded payments",
            "AUD",
            MetricId.PAYMENTS_AMOUNT,
            "Recorded tenders, not sales or physically counted cash."));
    for (String name :
        List.of(
            "Counted cash",
            "Cash to bank",
            "Takings variance",
            "Cash variance %",
            "Cash variance target min",
            "Cash variance target max")) {
      fields.add(
          missing(
              key(name),
              "Cash and takings",
              name,
              name.contains("%") || name.contains("target") ? "PERCENT" : "AUD",
              "Unavailable: physical counts, banking and an approved reconciliation basis are required."));
    }
    for (String name :
        List.of(
            "Kegs",
            "Wine",
            "Spirits",
            "Tinnies",
            "Other beverage",
            "Merchandise",
            "Beverage variance total",
            "Beverage variance %",
            "Beverage variance target min",
            "Beverage variance target max",
            "Meat",
            "Seafood",
            "Poultry",
            "Dairy",
            "Frozen",
            "Bread",
            "Food variance total",
            "Food variance %",
            "Food variance target min",
            "Food variance target max")) {
      fields.add(
          missing(
              key(name),
              "Stock variances",
              name,
              name.contains("%") || name.contains("target") ? "PERCENT" : "AUD",
              STOCK));
    }
    for (String name :
        List.of(
            "Beverage wastage",
            "Beverage wastage %",
            "Beverage wastage target %",
            "Food wastage",
            "Food wastage %",
            "Food wastage target %",
            "Total wastage target %")) {
      fields.add(
          missing(key(name), "Wastage", name, name.contains("%") ? "PERCENT" : "AUD", WASTE));
    }
    fields.add(
        new Field(
            "total_wastage",
            "Wastage",
            "Total recorded wastage",
            "AUD",
            MetricId.INVENTORY_WASTAGE,
            "Resolved recorded wastage only; missing records are not zero."));
    fields.add(missing("total_wastage_percent", "Wastage", "Total wastage %", "PERCENT", WASTE));
    fields.add(missing("staff_meals", "Wastage", "Staff meals and drinks cost", "AUD", WASTE));
    fields.add(
        new Field(
            "purchases",
            "Cost of goods",
            "Invoice purchases (not COGS)",
            "AUD",
            MetricId.INVENTORY_PURCHASES,
            "Known invoice purchases, not consumed cost or theoretical COGS."));
    for (String name :
        List.of(
            "Food theoretical COGS",
            "Food COGS %",
            "Food COGS YTD %",
            "Food COGS target %",
            "Beverage theoretical COGS",
            "Beverage COGS %",
            "Beverage COGS YTD %",
            "Beverage COGS target %",
            "Total theoretical COGS",
            "Total theoretical COGS %")) {
      fields.add(
          missing(
              key(name),
              "Cost of goods",
              name,
              name.contains("%") ? "PERCENT" : "AUD",
              "Unavailable: verified cost, tax, recipe and category bases are required; purchases are not COGS."));
    }
    fields.add(
        missing(
            "review_average",
            "Reviews",
            "Average review rating",
            "STARS",
            "Actual reported stars, grouped by review date in the venue timezone. Weekly average only; source coverage unverified."));
    return List.copyOf(fields);
  }

  private static String key(String name) {
    return name.toLowerCase(Locale.ROOT).replace("%", "percent").replace(' ', '_');
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public WeeklyReport report(UserRole role, LocalDate requestedFrom, int weekCount) {
    if (weekCount < 1 || weekCount > 13)
      throw new IllegalArgumentException("Select between 1 and 13 weeks");
    LocalDate monday = clock.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate from = requestedFrom == null ? monday.minusWeeks(weekCount) : requestedFrom;
    if (from.getDayOfWeek() != DayOfWeek.MONDAY || from.getYear() < 2000 || from.getYear() > 2100) {
      throw new IllegalArgumentException("Week start must be a Monday between 2000 and 2100");
    }
    LocalDate to = from.plusWeeks(weekCount).minusDays(1);
    List<Field> fields = fields();
    Set<String> resources = new LinkedHashSet<>();
    for (Field field : fields) {
      if (field.metric() != null) resources.addAll(catalog.requiredPermissions(field.metric()));
    }
    resources.add("reviews");
    for (String resource : resources)
      permissions.require(role, new ResourceKey(resource), PermissionAction.READ);
    TimeRange range =
        new TimeRange(from, to, com.goldys.platform.semantic.catalog.Calendar.CALENDAR);
    Map<MetricId, TimeSeriesResult> days = new HashMap<>(), totals = new HashMap<>();
    for (Field field : fields) {
      if (field.metric() == null) continue;
      days.put(field.metric(), query(field.metric(), range, TimeGrain.DAY));
      totals.put(field.metric(), query(field.metric(), range, TimeGrain.WEEK));
    }
    Map<LocalDate, BigDecimal> ratings = new HashMap<>();
    for (var bucket : reviews.ratingTrend(from, to))
      ratings.put(bucket.periodStart(), bucket.avgRating());
    List<WeeklyReport.Week> weeks = new ArrayList<>();
    for (int i = 0; i < weekCount; i++) {
      LocalDate start = from.plusWeeks(i);
      List<LocalDate> dates = start.datesUntil(start.plusDays(7)).toList();
      List<WeeklyReport.Row> rows = new ArrayList<>();
      for (Field field : fields)
        rows.add(row(field, start, dates, days, totals, ratings.get(start)));
      weeks.add(new WeeklyReport.Week(start, start.plusDays(6), dates, List.copyOf(rows)));
    }
    return new WeeklyReport(
        from, to, clock.clock().instant(), clock.zone().getId(), List.copyOf(weeks));
  }

  private TimeSeriesResult query(MetricId metric, TimeRange range, TimeGrain grain) {
    return (TimeSeriesResult) metrics.query(new MetricQuery(metric, range, grain, Set.of(), null));
  }

  private WeeklyReport.Row row(
      Field field,
      LocalDate start,
      List<LocalDate> dates,
      Map<MetricId, TimeSeriesResult> days,
      Map<MetricId, TimeSeriesResult> totals,
      BigDecimal rating) {
    List<BigDecimal> values = dates.stream().map(d -> (BigDecimal) null).toList();
    BigDecimal total = null;
    List<String> notes = new ArrayList<>();
    notes.add(field.note());
    if (!dates.getLast().isBefore(clock.today())) {
      notes.add("Week includes today or future dates; this is not a completed-week result.");
    }
    if (field.metric() != null) {
      TimeSeriesResult result = days.get(field.metric());
      Map<LocalDate, BigDecimal> byDate = points(result);
      values = dates.stream().map(byDate::get).toList();
      total = points(totals.get(field.metric())).get(start);
      List<LocalDate> missing = dates.stream().filter(d -> byDate.get(d) == null).toList();
      notes.add(
          missing.isEmpty()
              ? "All seven calendar dates have values; trading completeness is unverified."
              : "Known subtotal; unavailable dates: " + missing + ". Missing days are not zero.");
      notes.addAll(result.notices().stream().map(n -> "Selected range: " + n).toList());
      notes.add(
          "Metric: " + field.metric().value() + "; source: " + result.provenance().sourceDomain());
      var trust = result.provenance().trust();
      if (trust != null) {
        notes.add(
            "Selected-range trust: "
                + trust.state()
                + "; freshness: "
                + trust.freshness()
                + "; authoritative source: "
                + Objects.toString(trust.authoritativeSource(), "unknown"));
      }
    } else if (field.id().equals("review_average")) total = rating;
    return new WeeklyReport.Row(
        field.id(),
        field.section(),
        field.label(),
        field.unit(),
        values,
        total,
        total == null ? "UNAVAILABLE" : "QUALIFIED",
        List.copyOf(notes));
  }

  private static Map<LocalDate, BigDecimal> points(TimeSeriesResult result) {
    Map<LocalDate, BigDecimal> values = new HashMap<>();
    for (var series : result.series())
      for (MetricPoint point : series.points()) values.put(point.bucketStart(), point.value());
    return values;
  }
}
