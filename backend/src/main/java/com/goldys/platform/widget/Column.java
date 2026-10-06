package com.goldys.platform.widget;

import java.util.Objects;

/** A table column: key, display label, and optional value format. */
public record Column(String key, String label, String format) {
  public Column {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(label, "label");
  }
}
