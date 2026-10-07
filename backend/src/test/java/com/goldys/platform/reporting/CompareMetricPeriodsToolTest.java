package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.Comparison;
import com.goldys.platform.semantic.catalog.ComparisonService;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.BarChartWidgetSpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompareMetricPeriodsToolTest {

  private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
  private static final LocalDate JAN_7 = LocalDate.of(2026, 1, 7);
  private static final LocalDate JAN_8 = LocalDate.of(2026, 1, 8);
  private static final LocalDate JAN_14 = LocalDate.of(2026, 1, 14);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final MetricCatalog CATALOG = new MetricCatalog();
  private static final WidgetRenderer RENDERER = new WidgetRenderer(CATALOG);
  private static final ComparisonService COMPARISONS = new ComparisonService();

  private static CompareMetricPeriodsTool tool(MetricQueryService metrics) {
    return new CompareMetricPeriodsTool(metrics, RENDERER, COMPARISONS);
  }

  private static CompareMetricPeriodsInput input(Comparison comparison) {
    return new CompareMetricPeriodsInput(
        MetricId.SALES_GROSS, JAN_8, JAN_14, comparison, TimeGrain.DAY);
  }

  @Test
  void compareRendersTwoSeriesAndDelta() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenReturn(tsResult(MetricId.SALES_GROSS, JAN_8, JAN_14, point(JAN_8, "1100.00")))
        .thenReturn(tsResult(MetricId.SALES_GROSS, JAN_1, JAN_7, point(JAN_1, "1000.00")));

    ToolResult result = tool(metrics).execute(input(Comparison.PREVIOUS_WEEK), OWNER);

    assertThat(result.provenance()).hasSize(2);
    assertThat(result.provenance().get(0).range().from()).isEqualTo(JAN_8);
    assertThat(result.provenance().get(0).range().to()).isEqualTo(JAN_14);
    assertThat(result.provenance().get(1).range().from()).isEqualTo(JAN_1);
    assertThat(result.provenance().get(1).range().to()).isEqualTo(JAN_7);
    assertThat(result.relatedMetrics()).isEmpty();
    assertThat(result.notices()).anyMatch(n -> n.contains("vs"));
    assertThat(result.notices()).anyMatch(n -> n.contains("10.0%"));
    assertThat(result.widget()).isInstanceOf(BarChartWidgetSpec.class);
    assertThat(((BarChartWidgetSpec) result.widget()).series()).hasSize(2);
  }

  @Test
  void omitsDeltaWhenReferenceIsZero() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenReturn(tsResult(MetricId.SALES_GROSS, JAN_8, JAN_14, point(JAN_8, "1100.00")))
        .thenReturn(tsResult(MetricId.SALES_GROSS, JAN_1, JAN_7, point(JAN_1, "0.00")));

    ToolResult result = tool(metrics).execute(input(Comparison.PREVIOUS_WEEK), OWNER);

    assertThat(result.notices()).anyMatch(n -> n.contains("vs"));
    assertThat(result.notices()).noneMatch(n -> n.contains("%"));
  }

  @Test
  void omitsDeltaWhenCurrentHasNoResolvedData() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenReturn(tsResult(MetricId.SALES_GROSS, JAN_8, JAN_14, point(JAN_8, null)))
        .thenReturn(tsResult(MetricId.SALES_GROSS, JAN_1, JAN_7, point(JAN_1, "1000.00")));

    ToolResult result = tool(metrics).execute(input(Comparison.PREVIOUS_WEEK), OWNER);

    assertThat(result.notices()).anyMatch(n -> n.contains("vs"));
    assertThat(result.notices()).noneMatch(n -> n.contains("%"));
  }

  @Test
  void rejectsBudgetAndForecastWithoutQuerying() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    CompareMetricPeriodsTool tool = tool(metrics);

    assertThatThrownBy(() -> tool.execute(input(Comparison.BUDGET), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no data source yet");
    assertThatThrownBy(() -> tool.execute(input(Comparison.FORECAST), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no data source yet");

    verifyNoInteractions(metrics);
  }

  @Test
  void validatesInput() {
    assertThatThrownBy(
            () ->
                new CompareMetricPeriodsInput(
                    MetricId.SALES_GROSS, JAN_14, JAN_8, Comparison.PREVIOUS_WEEK, TimeGrain.DAY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("endDate");
    assertThatThrownBy(
            () ->
                new CompareMetricPeriodsInput(
                    null, JAN_8, JAN_14, Comparison.PREVIOUS_WEEK, TimeGrain.DAY))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new CompareMetricPeriodsInput(
                    MetricId.SALES_GROSS, JAN_8, JAN_14, null, TimeGrain.DAY))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new CompareMetricPeriodsInput(
                    MetricId.SALES_GROSS, JAN_8, JAN_14, Comparison.PREVIOUS_WEEK, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void toMetricQueriesReturnsCurrentRangeForPerMetricAuth() {
    CompareMetricPeriodsTool tool = tool(mock(MetricQueryService.class));

    List<MetricQuery> queries = tool.toMetricQueries(input(Comparison.PREVIOUS_WEEK));

    assertThat(queries).hasSize(1);
    MetricQuery q = queries.get(0);
    assertThat(q.metric()).isEqualTo(MetricId.SALES_GROSS);
    assertThat(q.range().from()).isEqualTo(JAN_8);
    assertThat(q.range().to()).isEqualTo(JAN_14);
    assertThat(q.grain()).isEqualTo(TimeGrain.DAY);
    assertThat(q.comparison()).isEqualTo(Comparison.PREVIOUS_WEEK);
  }

  @Test
  void rejectsWrongInputType() {
    CompareMetricPeriodsTool tool = tool(mock(MetricQueryService.class));

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CompareMetricPeriodsInput");
    assertThatThrownBy(() -> tool.toMetricQueries(new OtherInput()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CompareMetricPeriodsInput");
  }

  @Test
  void exposesStableIdentity() {
    CompareMetricPeriodsTool tool = tool(mock(MetricQueryService.class));

    assertThat(tool.id()).isEqualTo(ToolId.COMPARE_METRIC_PERIODS);
    assertThat(tool.name()).isEqualTo("compare_metric_periods");
    assertThat(tool.inputType()).isEqualTo(CompareMetricPeriodsInput.class);
    assertThat(tool.resource()).isEqualTo(new ResourceKey("conversational.chat"));
  }

  private static TimeSeriesResult tsResult(
      MetricId id, LocalDate from, LocalDate to, MetricPoint... points) {
    MetricProvenance provenance =
        new MetricProvenance(
            id,
            "1",
            new TimeRange(from, to, Calendar.CALENDAR),
            TimeGrain.DAY,
            "test",
            Instant.EPOCH,
            List.of(),
            "1");
    return new TimeSeriesResult(
        id, List.of(new MetricSeries(null, List.of(points))), List.of(), provenance);
  }

  private static MetricPoint point(LocalDate date, String value) {
    return new MetricPoint(date, value == null ? null : new BigDecimal(value));
  }

  private record OtherInput() implements ToolInput {}
}
