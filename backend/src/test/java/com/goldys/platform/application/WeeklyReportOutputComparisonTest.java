package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goldys.platform.auth.*;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.semantic.*;
import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import com.goldys.platform.semantic.WeeklyInventoryQuery.StockCount;
import com.goldys.platform.semantic.WeeklyInventoryQuery.Waste;
import com.goldys.platform.semantic.catalog.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Characterises complete report output before changing calculation ownership. */
class WeeklyReportOutputComparisonTest {
  // The additive assessment contract has separate assertions; preserve every existing byte.
  @JsonIgnoreProperties("assessments")
  private abstract static class FinancialBaseline {}

  private static final LocalDate MONDAY = LocalDate.parse("2026-10-26");
  private static final Instant NOW = Instant.parse("2026-11-04T01:30:00Z");
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  enum Scenario {
    COMPLETE,
    MIXED_TWO_WEEKS,
    EMPTY,
    COST_TAX_WITHDRAWN
  }

  private final MetricQueryService metrics = mock(MetricQueryService.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final ReviewMetricsQuery reviews = mock(ReviewMetricsQuery.class);
  private final WeeklyReportTargets targets = mock(WeeklyReportTargets.class);
  private final WeeklyInventoryQuery inventory = mock(WeeklyInventoryQuery.class);
  private final ReportingSalesQuery sales = mock(ReportingSalesQuery.class);

  @ParameterizedTest
  @EnumSource(Scenario.class)
  void preservesEveryReportFieldAndQualification(Scenario scenario) throws Exception {
    arrange(scenario);
    var service =
        new WeeklyReportService(
            metrics,
            new MetricCatalog(),
            reviews,
            permissions,
            VenueClock.fixed(ZoneId.of("Australia/Melbourne"), NOW),
            targets,
            inventory,
            sales,
            scenario != Scenario.COST_TAX_WITHDRAWN);
    // EMPTY also captures default selection of the last completed Melbourne week.
    var report =
        service.report(
            OWNER,
            scenario == Scenario.EMPTY ? null : MONDAY,
            scenario == Scenario.MIXED_TWO_WEEKS ? 2 : 1);
    assertScenario(report, scenario);
    assertBaseline(report, scenario);
  }

  private void arrange(Scenario scenario) {
    when(metrics.query(any())).thenAnswer(call -> metric(call.getArgument(0), scenario));
    when(reviews.ratingTrend(any(), any()))
        .thenReturn(
            scenario == Scenario.EMPTY
                ? List.of()
                : List.of(new RatingTrendBucket(MONDAY, decimal("4.67"), null)));
    when(targets.get(any(), any()))
        .thenAnswer(
            call -> {
              if (scenario == Scenario.EMPTY) return List.of();
              LocalDate week = call.getArgument(1);
              boolean second = week.isAfter(MONDAY);
              return List.of(
                  target("food_target", second ? "0" : "800", week),
                  target("beverage_target", "300", week),
                  target("sales_target", second ? "1200" : "1000", week),
                  target("food_wastage_target_percent", "0.025", week),
                  target("food_cogs_target_percent", "0.30", week));
            });
    var observations = new ArrayList<Sale>();
    var waste = new ArrayList<Waste>();
    int days = scenario == Scenario.MIXED_TWO_WEEKS ? 10 : 7;
    if (scenario != Scenario.EMPTY) {
      for (int day = 0; day < days; day++) {
        LocalDate date = MONDAY.plusDays(day);
        if (scenario == Scenario.MIXED_TWO_WEEKS && day == 4) continue;
        observations.addAll(daySales(date, day, scenario));
        waste.add(
            new Waste(
                date, day % 2 == 0 ? " Knockoff " : "Knock off", 1, 1, decimal("2.50"), "FOOD"));
        waste.add(new Waste(date, null, 1, 1, decimal("1.25"), "BEVERAGE"));
      }
      if (scenario == Scenario.MIXED_TWO_WEEKS) {
        waste.add(new Waste(MONDAY.plusDays(2), "KO", 2, 1, decimal("0.75"), "UNKNOWN"));
        waste.add(new Waste(MONDAY.plusDays(6), "Spoiled", 1, 0, null, "FOOD"));
        waste.add(new Waste(MONDAY.plusDays(7), "Other", 1, 1, decimal("0.50"), "OTHER"));
      }
    }
    when(sales.salesWithCosts(any(), any())).thenReturn(observations);
    when(inventory.wastage(any(), any())).thenReturn(waste);
    when(inventory.stockCounts(any(), any()))
        .thenReturn(
            scenario == Scenario.EMPTY
                ? List.of()
                : List.of(
                    new StockCount(MONDAY, 40, 3, 2, decimal("123.45"), null, null),
                    new StockCount(MONDAY.plusDays(7), 41, 1, 1, BigDecimal.ZERO, true, false)));
  }

  private static List<Sale> daySales(LocalDate date, int day, Scenario scenario) {
    var food =
        new Sale(date, "food", decimal("110"), decimal("100"), "CTB", "FOOD", decimal("25"), 1, 1);
    var beverage =
        new Sale(
            date, "drink", decimal("55"), decimal("50"), "CTB", "BEVERAGE", decimal("10"), 1, 1);
    if (scenario != Scenario.MIXED_TWO_WEEKS) return List.of(food, beverage);
    return switch (day) {
      case 0 ->
          List.of(
              new Sale(
                  date,
                  "refund",
                  decimal("-11"),
                  decimal("-10"),
                  "CTB",
                  "FOOD",
                  decimal("-2.50"),
                  1,
                  1),
              beverage);
      case 1 ->
          List.of(food, new Sale(date, "missing tax", decimal("55"), null, "CTB", "BEVERAGE"));
      case 2 ->
          List.of(
              food,
              beverage,
              new Sale(date, "unreviewed", decimal("33"), decimal("30"), "CTB", "UNKNOWN"));
      case 3 -> List.of(food, new Sale(date, "conflict", null, null, null, "BEVERAGE"));
      case 5 ->
          List.of(
              new Sale(
                  date,
                  "observed zero",
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  "CTB",
                  "FOOD",
                  BigDecimal.ZERO,
                  1,
                  1));
      case 6 ->
          List.of(
              new Sale(
                  date,
                  "partial cost",
                  decimal("110"),
                  decimal("100"),
                  "CTB",
                  "FOOD",
                  decimal("5"),
                  2,
                  1),
              beverage);
      default -> List.of(food, beverage);
    };
  }

  private static TimeSeriesResult metric(MetricQuery query, Scenario scenario) {
    BigDecimal daily =
        switch (query.metric()) {
          case SALES_NET -> decimal("150");
          case SALES_GST -> decimal("15");
          case SALES_GROSS -> decimal("165");
          case PAYMENTS_AMOUNT -> decimal("164.50");
          case INVENTORY_PURCHASES -> decimal("20");
          default -> throw new AssertionError("Unexpected report metric: " + query.metric());
        };
    var points = new ArrayList<MetricPoint>();
    if (scenario != Scenario.EMPTY) {
      for (LocalDate date :
          query.range().from().datesUntil(query.range().to().plusDays(1)).toList()) {
        long offset = java.time.temporal.ChronoUnit.DAYS.between(MONDAY, date);
        if (query.grain() == TimeGrain.WEEK) {
          if (date.getDayOfWeek() == DayOfWeek.MONDAY)
            // Deliberately supplied catalogue totals: report assembly must not sum daily points.
            points.add(new MetricPoint(date, daily.multiply(decimal("7")).add(decimal("0.12"))));
        } else if (scenario != Scenario.MIXED_TWO_WEEKS || offset != 4 && offset < 10) {
          points.add(
              new MetricPoint(
                  date,
                  scenario == Scenario.MIXED_TWO_WEEKS && offset == 1
                      ? null
                      : scenario == Scenario.MIXED_TWO_WEEKS && offset == 5
                          ? BigDecimal.ZERO
                          : daily));
        }
      }
    }
    var provenance =
        new MetricProvenance(
            query.metric(),
            "1",
            query.range(),
            query.grain(),
            "resolved_test",
            Instant.EPOCH,
            List.of(),
            "1");
    if (scenario == Scenario.MIXED_TWO_WEEKS)
      provenance =
          provenance.withTrust(
              new TrustSummary(
                  TrustState.CONFLICTED,
                  FreshnessState.STALE,
                  null,
                  Instant.EPOCH,
                  Instant.EPOCH,
                  Duration.ofDays(1)));
    return new TimeSeriesResult(
        query.metric(),
        List.of(new MetricSeries(null, points)),
        List.of("Source coverage unverified"),
        provenance);
  }

  private static WeeklyReportTargets.Target target(String id, String value, LocalDate week) {
    return new WeeklyReportTargets.Target(
        id, decimal(value), week.minusWeeks(1), "Synthetic target");
  }

  private static BigDecimal decimal(String value) {
    return new BigDecimal(value);
  }

  private static void assertScenario(WeeklyReport report, Scenario scenario) {
    for (var week : report.weeks()) {
      assertThat(week.assessments().keySet())
          .containsExactlyInAnyOrderElementsOf(
              week.rows().stream().map(WeeklyReport.Row::id).toList());
      assertThat(week.assessments().values())
          .allSatisfy(
              a -> {
                assertThat(a.outcome()).isNotNull();
                if (a.outcome() == WeeklyReport.AssessmentOutcome.WITHHELD)
                  assertThat(a.reason()).isNotNull();
                else assertThat(a.reason()).isNull();
              });
    }
    var rows = report.weeks().getFirst().rows();
    assertThat(rows).hasSize(85);
    assertThat(rows).extracting(WeeklyReport.Row::id).doesNotHaveDuplicates();
    var byId = new HashMap<String, WeeklyReport.Row>();
    rows.forEach(row -> byId.put(row.id(), row));
    if (scenario == Scenario.EMPTY) {
      assertThat(report.from()).isEqualTo(MONDAY);
      assertThat(rows).allSatisfy(row -> assertThat(row.total()).isNull());
    } else if (scenario == Scenario.MIXED_TWO_WEEKS) {
      assertThat(report.weeks()).hasSize(2);
      assertThat(byId.get("food_sales").days()).containsNull();
      assertThat(byId.get("food_sales").days().getFirst()).isEqualByComparingTo("-10");
      assertThat(byId.get("food_sales").days().get(5)).isEqualByComparingTo("0");
      assertThat(byId.get("net_sales").days().get(4)).isNull();
      assertThat(byId.get("total_theoretical_cogs").total()).isNull();
      assertThat(report.weeks().getLast().stockCounts()).hasSize(1);
    } else {
      assertThat(byId.get("food_sales").total()).isEqualByComparingTo("700");
      assertThat(byId.get("food_difference").total()).isEqualByComparingTo("-100");
      assertThat(byId.get("net_sales").total()).isEqualByComparingTo("1050.12");
      assertThat(byId.get("food_theoretical_cogs").total()).isEqualByComparingTo("175");
      if (scenario == Scenario.COST_TAX_WITHDRAWN)
        assertThat(byId.get("food_cogs_percent").total()).isNull();
      else assertThat(byId.get("food_cogs_percent").total()).isEqualByComparingTo("0.25");
    }
  }

  private static void assertBaseline(WeeklyReport report, Scenario scenario) throws Exception {
    String name = scenario.name().toLowerCase(Locale.ROOT) + ".json";
    String actual = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
    Path output = Path.of("build/reports/weekly-report-output", name);
    Files.createDirectories(output.getParent());
    Files.writeString(output, actual, StandardCharsets.UTF_8);
    // Baselines are committed separately; tests never create or update expected output.
    try (var input =
        WeeklyReportOutputComparisonTest.class.getResourceAsStream(
            "/weekly-report-output/" + name)) {
      assertThat(input).as("Committed baseline %s (actual: %s)", name, output).isNotNull();
      String financialOutput =
          JSON.copy()
                  .addMixIn(WeeklyReport.Week.class, FinancialBaseline.class)
                  .writerWithDefaultPrettyPrinter()
                  .writeValueAsString(report)
              + "\n";
      assertThat(financialOutput)
          .as("Complete output for %s; inspect %s", scenario, output)
          .isEqualTo(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }
  }
}
