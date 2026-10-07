package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ComparisonServiceTest {

  @Test
  void previousWeekShiftsBothBoundsBackSevenDays() {
    ComparisonService service = new ComparisonService();
    MetricQuery current =
        new MetricQuery(
            MetricId.SALES_GROSS,
            new TimeRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13), Calendar.CALENDAR),
            TimeGrain.DAY,
            java.util.Set.of(),
            Comparison.PREVIOUS_WEEK);

    TimeRange reference = service.referenceRange(current);

    assertThat(reference.from()).isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(reference.to()).isEqualTo(LocalDate.of(2026, 9, 6));
  }

  @Test
  void sameWeekdayLastWeekIsSevenDaysBack() {
    ComparisonService service = new ComparisonService();
    TimeRange r =
        service.referenceRange(
            new MetricQuery(
                MetricId.SALES_GROSS,
                new TimeRange(
                    LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), Calendar.CALENDAR),
                TimeGrain.DAY,
                java.util.Set.of(),
                Comparison.SAME_WEEKDAY_LAST_WEEK));
    assertThat(r.from()).isEqualTo(LocalDate.of(2026, 8, 31));
  }
}
