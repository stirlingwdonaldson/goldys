package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.semantic.RatingTrendBucket;
import com.goldys.platform.semantic.ReviewMetricsQuery;
import com.goldys.platform.semantic.catalog.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WeeklyReportServiceTest {
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
  private final MetricQueryService metrics = mock(MetricQueryService.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final ReviewMetricsQuery reviews = mock(ReviewMetricsQuery.class);
  private final WeeklyReportTargets targets = mock(WeeklyReportTargets.class);
  private final com.goldys.platform.semantic.WeeklyInventoryQuery inventory =
      mock(com.goldys.platform.semantic.WeeklyInventoryQuery.class);
  private final com.goldys.platform.semantic.ReportingSalesQuery reportingSales =
      mock(com.goldys.platform.semantic.ReportingSalesQuery.class);
  private final WeeklyReportService service =
      new WeeklyReportService(
          metrics,
          new MetricCatalog(),
          reviews,
          permissions,
          VenueClock.fixed(ZoneId.of("Australia/Melbourne"), Instant.parse("2026-11-01T14:30:00Z")),
          targets,
          inventory,
          reportingSales,
          true);

  @BeforeEach
  void data() {
    when(reviews.ratingTrend(any(), any()))
        .thenReturn(List.of(new RatingTrendBucket(MONDAY, new BigDecimal("4.67"), null)));
    when(metrics.query(any()))
        .thenAnswer(
            inv -> {
              MetricQuery q = inv.getArgument(0);
              List<MetricPoint> points =
                  q.grain() == TimeGrain.WEEK
                      ? q.range()
                          .from()
                          .datesUntil(q.range().to().plusDays(1), Period.ofWeeks(1))
                          .map(d -> new MetricPoint(d, new BigDecimal("999.12")))
                          .toList()
                      : q.range()
                          .from()
                          .datesUntil(q.range().to().plusDays(1))
                          .map(
                              d ->
                                  new MetricPoint(
                                      d,
                                      d.equals(q.range().from().plusDays(1))
                                          ? null
                                          : new BigDecimal("100")))
                          .toList();
              return new TimeSeriesResult(
                  q.metric(),
                  List.of(new MetricSeries(null, points)),
                  List.of("Source coverage unverified"),
                  new MetricProvenance(
                      q.metric(),
                      "1",
                      q.range(),
                      q.grain(),
                      "resolved_test",
                      Instant.EPOCH,
                      List.of(q.range().from().plusDays(1)),
                      "1"));
            });
  }

  @Test
  void defaultsToCompletedMelbourneWeeksAndRetainsNullsAndCatalogueTotals() {
    WeeklyReport report = service.report(OWNER, null, 1);
    assertThat(report.from()).isEqualTo(MONDAY);
    assertThat(report.to()).isEqualTo(MONDAY.plusDays(6));
    assertThat(report.timezone()).isEqualTo("Australia/Melbourne");
    WeeklyReport.Row sales = row(report, "net_sales");
    assertThat(sales.days()).hasSize(7);
    assertThat(sales.days().get(1)).isNull();
    assertThat(sales.total()).isEqualByComparingTo("999.12");
    assertThat(sales.status()).isEqualTo("QUALIFIED");
    assertThat(sales.notes()).anyMatch(n -> n.contains("2026-10-27"));
    assertThat(sales.notes()).anyMatch(n -> n.contains("Source coverage unverified"));
  }

  @Test
  void withholdsUnverifiedFieldsAndKeepsWeeklyReviewAverage() {
    WeeklyReport report = service.report(OWNER, MONDAY, 2);
    assertThat(report.weeks()).hasSize(2);
    for (String id :
        List.of(
            "food_sales",
            "beverage_sales",
            "food_target",
            "takings_variance",
            "food_theoretical_cogs")) {
      assertThat(row(report, id).total()).isNull();
      assertThat(row(report, id).days()).containsOnlyNulls();
      assertThat(row(report, id).status()).isEqualTo("UNAVAILABLE");
    }
    assertThat(row(report, "review_average").total()).isEqualByComparingTo("4.67");
    assertThat(row(report, "review_average").days()).containsOnlyNulls();
    assertThat(report.weeks().getFirst().rows())
        .extracting(WeeklyReport.Row::id)
        .doesNotHaveDuplicates();
    assertThat(report.weeks().getFirst().rows())
        .noneMatch(r -> r.label().matches("(?i).*(labour|wages|prime cost|happiness).*"));
    verify(permissions)
        .require(OWNER, new ResourceKey("reconciliation.sales"), PermissionAction.READ);
    verify(permissions).require(OWNER, new ResourceKey("inventory.cost"), PermissionAction.READ);
    verify(permissions).require(OWNER, new ResourceKey("payments.metrics"), PermissionAction.READ);
    verify(permissions).require(OWNER, new ResourceKey("reviews"), PermissionAction.READ);
  }

  @Test
  void classifiedActualsUseEffectiveFoodAndBeverageTargets() {
    var observations =
        new java.util.ArrayList<com.goldys.platform.semantic.ReportingSalesQuery.Sale>();
    MONDAY
        .datesUntil(MONDAY.plusDays(7))
        .forEach(
            d -> {
              observations.add(
                  new com.goldys.platform.semantic.ReportingSalesQuery.Sale(
                      d, "food", new BigDecimal("110"), new BigDecimal("100"), "CTB", "FOOD"));
              observations.add(
                  new com.goldys.platform.semantic.ReportingSalesQuery.Sale(
                      d, "drink", new BigDecimal("55"), new BigDecimal("50"), "CTB", "BEVERAGE"));
            });
    when(reportingSales.salesWithCosts(any(), any())).thenReturn(observations);
    when(targets.get(OWNER, MONDAY))
        .thenReturn(
            List.of(
                new WeeklyReportTargets.Target(
                    "food_target", new BigDecimal("800"), MONDAY, "reviewed"),
                new WeeklyReportTargets.Target(
                    "beverage_target", new BigDecimal("300"), MONDAY, "reviewed")));
    var report = service.report(OWNER, MONDAY, 1);
    assertThat(row(report, "food_sales").total()).isEqualByComparingTo("700");
    assertThat(row(report, "beverage_sales").total()).isEqualByComparingTo("350");
    assertThat(row(report, "food_difference").total()).isEqualByComparingTo("-100");
    assertThat(row(report, "beverage_difference").total()).isEqualByComparingTo("50");
    assertThat(row(report, "food_difference_percent").total()).isEqualByComparingTo("-0.125");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "reconciliation.sales",
        "inventory.cost",
        "payments.metrics",
        "reviews",
        "weekly-report.targets",
        "inventory.stock",
        "products.category"
      })
  void deniesBeforeReadingAnyData(String resource) {
    doThrow(AccessDeniedException.forResource(resource))
        .when(permissions)
        .require(OWNER, new ResourceKey(resource), PermissionAction.READ);
    assertThatThrownBy(() -> service.report(OWNER, MONDAY, 1))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(metrics, reviews, reportingSales, inventory, targets);
  }

  @Test
  void validatesWeekBoundariesAndBoundsBeforeQuerying() {
    for (int count : List.of(0, -1, 14, Integer.MAX_VALUE)) {
      assertThatThrownBy(() -> service.report(OWNER, MONDAY, count))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> service.report(OWNER, MONDAY.plusDays(1), 1))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(metrics, reviews, permissions);
  }

  private static WeeklyReport.Row row(WeeklyReport report, String id) {
    return report.weeks().getFirst().rows().stream()
        .filter(r -> r.id().equals(id))
        .findFirst()
        .orElseThrow();
  }
}
