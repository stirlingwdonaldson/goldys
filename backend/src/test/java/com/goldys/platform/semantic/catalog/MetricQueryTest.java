package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MetricQueryTest {

  private static final TimeRange RANGE =
      new TimeRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13), Calendar.TRADING);

  @Test
  void exposesStableDottedIds() {
    assertThat(MetricId.SALES_GROSS.value()).isEqualTo("sales.gross");
    assertThat(MetricId.RESERVATIONS_NO_SHOW_RATE.value()).isEqualTo("reservations.no_show_rate");
  }

  @Test
  void rejectsRangeWithToBeforeFrom() {
    assertThatThrownBy(
            () ->
                new TimeRange(
                    LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 7), Calendar.TRADING))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("from");
  }

  @Test
  void defaultsToEmptyDimensionsWhenNull() {
    MetricQuery q = new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, null, null);
    assertThat(q.dimensions()).isEmpty();
    assertThat(q.comparison()).isNull();
  }
}
