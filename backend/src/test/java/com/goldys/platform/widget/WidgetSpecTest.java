package com.goldys.platform.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WidgetSpecTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void timeSeriesSerializesAsAVersionedDiscriminatedWidget() throws Exception {
    WidgetSpec widget =
        new TimeSeriesWidgetSpec(
            "w1",
            "Daily sales",
            "Resolved gross sales per day.",
            List.of(
                new Series(
                    "grossSales",
                    "Gross sales",
                    List.of(new Point("2026-09-13", new BigDecimal("27650.66"))))),
            "currency",
            null);

    JsonNode node = mapper.readTree(mapper.writeValueAsString(widget));

    assertThat(node.get("schemaVersion").asInt()).isEqualTo(2);
    assertThat(node.get("type").asText()).isEqualTo("time-series");
    assertThat(node.get("title").asText()).isEqualTo("Daily sales");
    assertThat(node.get("series").get(0).get("key").asText()).isEqualTo("grossSales");
    assertThat(node.get("series").get(0).get("points").get(0).get("x").asText())
        .isEqualTo("2026-09-13");
    assertThat(node.get("series").get(0).get("points").get(0).get("y").decimalValue())
        .isEqualByComparingTo("27650.66");
  }

  @Test
  void everyVariantReportsSchemaVersionTwo() {
    assertThat(new StatWidgetSpec("id", "t", null, null, null, null, null).schemaVersion())
        .isEqualTo(2);
    assertThat(new RankedListWidgetSpec("id", "t", null, List.of(), null).schemaVersion())
        .isEqualTo(2);
  }

  @Test
  void statRequiresIdAndTitle() {
    assertThatThrownBy(() -> new StatWidgetSpec(null, "t", null, null, null, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new StatWidgetSpec("id", null, null, null, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void widgetQueryCopiesInputDefensively() {
    var query = new WidgetQuery("get_sales_by_period", java.util.Map.of("a", "b"));
    assertThat(query.tool()).isEqualTo("get_sales_by_period");
    assertThat(query.input()).containsEntry("a", "b");
  }
}
