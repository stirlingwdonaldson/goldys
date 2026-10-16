package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.semantic.*;
import com.goldys.platform.semantic.catalog.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PeriodSummaryRegressionTest {
  @Test
  void summaryAndSeriesBothWithholdRatioAcrossMismatchedDays() {
    LocalDate first = LocalDate.of(2026, 10, 1), last = first.plusDays(1);
    var catalog = new MetricCatalog();
    var sales = mock(SalesMetricsQuery.class);
    var reservations = mock(ReservationMetricsQuery.class);
    var labour = mock(LabourMetricsQuery.class);
    var products = mock(ProductMetricsQuery.class);
    when(labour.dailyLabour(first, last))
        .thenReturn(
            List.of(
                new LabourMetric(
                    first,
                    "FOH",
                    new BigDecimal("8"),
                    new BigDecimal("8"),
                    null,
                    new BigDecimal("80"),
                    "DEPUTY",
                    false),
                new LabourMetric(
                    last,
                    "FOH",
                    new BigDecimal("8"),
                    new BigDecimal("8"),
                    null,
                    new BigDecimal("80"),
                    "DEPUTY",
                    false)));
    when(sales.dailySales(first, last))
        .thenReturn(
            List.of(
                new DailySalesMetric(
                    first,
                    new BigDecimal("1000"),
                    new BigDecimal("900"),
                    new BigDecimal("100"),
                    "single",
                    false)));
    when(reservations.dailySummaries(first, last))
        .thenReturn(
            List.of(new ReservationSummary(first, 10, 10, 20, 0, 0, 0, 20, null, null, null)));
    var labourExec = new LabourMetricExecutor(labour, catalog);
    var derived =
        new DerivedMetricExecutor(
            new ReservationMetricExecutor(reservations, catalog),
            new SalesMetricExecutor(sales, catalog),
            labourExec,
            new InventoryMetricExecutor(mock(InventoryMetricsQuery.class), catalog),
            new ProductMetricExecutor(products, catalog),
            new FoodBevMetricExecutor(mock(ProductCategoryMetricsQuery.class), catalog),
            labour,
            products,
            catalog);
    MetricQueryService queries =
        q -> labourExec.ids().contains(q.metric()) ? labourExec.evaluate(q) : derived.evaluate(q);
    var service = new LabourReportingService(queries, mock(PermissionService.class));
    var owner = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    var summary = service.summary(owner, first, last);
    var series =
        (TimeSeriesResult)
            queries.query(
                new MetricQuery(
                    MetricId.LABOUR_FOH_PERCENT,
                    new TimeRange(
                        first, last, com.goldys.platform.semantic.catalog.Calendar.CALENDAR),
                    TimeGrain.TOTAL,
                    Set.of(),
                    null));
    assertThat(summary.labourCost()).isEqualByComparingTo("160");
    assertThat(summary.fohLabourCostPercent()).isNull();
    assertThat(summary.hoursPerCover()).isNull();
    assertThat(series.series().getFirst().points().getFirst().value()).isNull();
    assertThat(summary.notices()).anyMatch(n -> n.contains("excluded days"));
  }
}
