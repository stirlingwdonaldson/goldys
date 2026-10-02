package com.goldys.platform.reporting;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A widget spec conforming to widget-spec.schema.json version 1. */
public record WidgetSpec(
    int version, String type, String title, String description, List<Map<String, Object>> data) {
  private static final List<String> TYPES = List.of("stat", "table", "line-chart", "bar-chart");

  public WidgetSpec {
    if (version != 1) {
      throw new IllegalArgumentException("Unsupported widget version: " + version);
    }
    if (type == null || !TYPES.contains(type)) {
      throw new IllegalArgumentException("Unknown widget type: " + type);
    }
    Objects.requireNonNull(title, "title");
    data = data == null ? List.of() : List.copyOf(data);
  }
}
