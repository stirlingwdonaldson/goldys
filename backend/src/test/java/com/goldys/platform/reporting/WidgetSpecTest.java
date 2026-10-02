package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WidgetSpecTest {

  @Test
  void acceptsAValidLineChart() {
    WidgetSpec spec =
        new WidgetSpec(
            1,
            "line-chart",
            "Daily sales",
            "Resolved gross sales per day.",
            List.of(Map.of("date", "2026-09-13", "grossSales", "27650.66")));

    assertThat(spec.version()).isEqualTo(1);
    assertThat(spec.type()).isEqualTo("line-chart");
    assertThat(spec.data()).hasSize(1);
  }

  @Test
  void rejectsAnUnknownWidgetType() {
    assertThatThrownBy(() -> new WidgetSpec(1, "pie", "Sales", null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("widget type");
  }

  @Test
  void rejectsAnUnsupportedVersion() {
    assertThatThrownBy(() -> new WidgetSpec(2, "stat", "Sales", null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }
}
