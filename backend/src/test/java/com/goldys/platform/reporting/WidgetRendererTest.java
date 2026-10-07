package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.semantic.catalog.*;
import com.goldys.platform.widget.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WidgetRendererTest {
  private final WidgetRenderer renderer = new WidgetRenderer(new MetricCatalog());

  private static TimeSeriesResult ts(MetricId id, BigDecimal v) {
    return new TimeSeriesResult(
        id,
        List.of(new MetricSeries(null, List.of(new MetricPoint(LocalDate.of(2026, 9, 13), v)))),
        List.of(),
        new MetricProvenance(
            id,
            "1",
            new TimeRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), Calendar.CALENDAR),
            TimeGrain.DAY,
            "x",
            java.time.Instant.EPOCH,
            List.of(),
            "1"));
  }

  @Test
  void rendersSingleMetricAsTimeSeries() {
    WidgetSpec spec =
        renderer.render(
            "w1", "time-series", List.of(ts(MetricId.SALES_GROSS, new BigDecimal("100"))));
    assertThat(spec).isInstanceOf(TimeSeriesWidgetSpec.class);
    assertThat(((TimeSeriesWidgetSpec) spec).series()).hasSize(1);
  }

  @Test
  void rendersCompositeAsTableWithOneColumnPerMetric() {
    WidgetSpec spec =
        renderer.render(
            "w1",
            "table",
            List.of(
                ts(MetricId.INVENTORY_PURCHASES, new BigDecimal("10")),
                ts(MetricId.INVENTORY_WASTAGE, new BigDecimal("2"))));
    assertThat(spec).isInstanceOf(TableWidgetSpec.class);
    assertThat(((TableWidgetSpec) spec).columns()).hasSize(2);
  }

  @Test
  void rejectsUnsupportedRenderType() {
    assertThatThrownBy(
            () ->
                renderer.render(
                    "w1", "ranked-list", List.of(ts(MetricId.SALES_GROSS, new BigDecimal("1")))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
