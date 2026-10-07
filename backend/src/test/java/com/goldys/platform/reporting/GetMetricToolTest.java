package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricRankedItem;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.RankedListResult;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.RankedListWidgetSpec;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GetMetricToolTest {

  private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
  private static final LocalDate JAN_2 = LocalDate.of(2026, 1, 2);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final MetricCatalog CATALOG = new MetricCatalog();
  private static final WidgetRenderer RENDERER = new WidgetRenderer(CATALOG);

  @Test
  void getMetricRendersAndCarriesProvenanceAndRelated() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenReturn(
            tsResult(MetricId.SALES_GROSS, point(JAN_1, "27650.66"), point(JAN_2, "31200.00")));

    ToolResult result =
        new GetMetricTool(metrics, RENDERER, CATALOG)
            .execute(
                new GetMetricInput(MetricId.SALES_GROSS, JAN_1, JAN_2, TimeGrain.DAY, Set.of()),
                OWNER);

    assertThat(result.provenance()).isNotEmpty();
    assertThat(result.widget()).isInstanceOf(TimeSeriesWidgetSpec.class);
    TimeSeriesWidgetSpec widget = (TimeSeriesWidgetSpec) result.widget();
    assertThat(widget.series()).hasSize(1);
    assertThat(widget.series().get(0).points()).hasSize(2);
  }

  @Test
  void rendersRankedListMetricsAsRankedListWidget() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenReturn(
            rankedResult(
                MetricId.PRODUCT_TOP_SELLERS,
                new MetricRankedItem("Steak", new BigDecimal("1200.00"), null, false),
                new MetricRankedItem("Wine", new BigDecimal("900.00"), null, true)));

    ToolResult result =
        new GetMetricTool(metrics, RENDERER, CATALOG)
            .execute(
                new GetMetricInput(
                    MetricId.PRODUCT_TOP_SELLERS, JAN_1, JAN_2, TimeGrain.DAY, Set.of()),
                OWNER);

    assertThat(result.widget()).isInstanceOf(RankedListWidgetSpec.class);
    RankedListWidgetSpec widget = (RankedListWidgetSpec) result.widget();
    assertThat(widget.items()).hasSize(2);
    assertThat(widget.items().get(0).label()).isEqualTo("Steak");
    assertThat(widget.items().get(1).badge()).isEqualTo("unresolved");
  }

  @Test
  void rejectsBackwardsDateRange() {
    assertThatThrownBy(
            () -> new GetMetricInput(MetricId.SALES_GROSS, JAN_2, JAN_1, TimeGrain.DAY, Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullMetricAndGrain() {
    assertThatThrownBy(() -> new GetMetricInput(null, JAN_1, JAN_2, TimeGrain.DAY, Set.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new GetMetricInput(MetricId.SALES_GROSS, JAN_1, JAN_2, null, Set.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void defaultsNullDimensionsToEmpty() {
    GetMetricInput input =
        new GetMetricInput(MetricId.SALES_GROSS, JAN_1, JAN_2, TimeGrain.DAY, null);

    assertThat(input.dimensions()).isEmpty();
  }

  @Test
  void forwardsCatalogueValidationForUnsupportedDimension() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenThrow(
            new IllegalArgumentException(
                "dimensions [SERVICE_PERIOD] are not allowed for sales.gross"));
    GetMetricTool tool = new GetMetricTool(metrics, RENDERER, CATALOG);

    assertThatThrownBy(
            () ->
                tool.execute(
                    new GetMetricInput(
                        MetricId.SALES_GROSS,
                        JAN_1,
                        JAN_2,
                        TimeGrain.DAY,
                        Set.of(Dimension.SERVICE_PERIOD)),
                    OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SERVICE_PERIOD");
  }

  private static TimeSeriesResult tsResult(MetricId id, MetricPoint... points) {
    return new TimeSeriesResult(
        id, List.of(new MetricSeries(null, List.of(points))), List.of(), provenance(id));
  }

  private static RankedListResult rankedResult(MetricId id, MetricRankedItem... items) {
    return new RankedListResult(id, List.of(items), List.of(), provenance(id));
  }

  private static MetricProvenance provenance(MetricId id) {
    return new MetricProvenance(
        id,
        "1",
        new TimeRange(JAN_1, JAN_2, Calendar.CALENDAR),
        TimeGrain.DAY,
        "test",
        Instant.EPOCH,
        List.of(),
        "1");
  }

  private static MetricPoint point(LocalDate date, String value) {
    return new MetricPoint(date, value == null ? null : new BigDecimal(value));
  }
}
