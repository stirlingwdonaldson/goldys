package com.goldys.platform.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Objects;

/** A single metric value with an optional format hint and caption. */
public record StatWidgetSpec(
    String id,
    String title,
    String description,
    BigDecimal value,
    String format,
    String hint,
    WidgetQuery query)
    implements WidgetSpec {

  public StatWidgetSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(title, "title");
  }

  @Override
  @JsonProperty("schemaVersion")
  public int schemaVersion() {
    return SCHEMA_VERSION;
  }
}
