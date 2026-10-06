package com.goldys.platform.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A tabular widget: an explicit column contract plus row maps keyed by column key. */
public record TableWidgetSpec(
    String id,
    String title,
    String description,
    List<Column> columns,
    List<Map<String, Object>> rows,
    WidgetQuery query)
    implements WidgetSpec {

  public TableWidgetSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(title, "title");
    columns = columns == null ? List.of() : List.copyOf(columns);
    rows = rows == null ? List.of() : List.copyOf(rows);
  }

  @Override
  @JsonProperty("schemaVersion")
  public int schemaVersion() {
    return SCHEMA_VERSION;
  }
}
