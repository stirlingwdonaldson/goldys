package com.goldys.platform.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/** An ordered list of labeled items with optional primary/secondary values and badges. */
public record RankedListWidgetSpec(
    String id, String title, String description, List<RankedItem> items, WidgetQuery query)
    implements WidgetSpec {

  public RankedListWidgetSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(title, "title");
    items = items == null ? List.of() : List.copyOf(items);
  }

  @Override
  @JsonProperty("schemaVersion")
  public int schemaVersion() {
    return SCHEMA_VERSION;
  }
}
