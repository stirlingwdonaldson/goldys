package com.goldys.platform.widget;

import java.util.Map;
import java.util.Objects;

/**
 * The bounded semantic query behind a widget: a fixed tool id plus its typed input, stored as a
 * plain map so a saved dashboard can be re-run through the tool dispatcher. Never a free-form SQL
 * or filter expression.
 */
public record WidgetQuery(String tool, Map<String, Object> input) {
  public WidgetQuery {
    Objects.requireNonNull(tool, "tool");
    input = input == null ? Map.of() : Map.copyOf(input);
  }
}
