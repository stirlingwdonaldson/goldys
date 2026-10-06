package com.goldys.platform.dashboard;

import java.util.Map;
import java.util.Objects;

/**
 * One persisted widget: a fixed semantic tool plus its bounded input. Stored as JSONB inside a
 * dashboard; never generated code, never free-form SQL/filters.
 */
public record SavedWidget(String id, String tool, Map<String, Object> input) {
  public SavedWidget {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(tool, "tool");
    input = input == null ? Map.of() : input;
  }
}
