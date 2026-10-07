package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricResultTest {

  @Test
  void constructsATimeSeriesResultWithProvenance() {
    MetricProvenance provenance =
        new MetricProvenance(
            MetricId.SALES_GROSS,
            "1",
            new TimeRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_daily_sales",
            Instant.parse("2026-09-13T06:00:00Z"),
            List.of(),
            "1");

    TimeSeriesResult result =
        new TimeSeriesResult(
            MetricId.SALES_GROSS,
            List.of(
                new MetricSeries(
                    null,
                    List.of(new MetricPoint(LocalDate.of(2026, 9, 7), new BigDecimal("100.00")))),
                new MetricSeries("DINNER", List.of())),
            List.of("1 day unresolved"),
            provenance);

    assertThat(result.metric()).isEqualTo(MetricId.SALES_GROSS);
    assertThat(result.notices()).containsExactly("1 day unresolved");
    assertThat(result.series()).hasSize(2);
  }
}
