package com.goldys.platform.widget;

import java.util.List;
import java.util.Objects;

/** One named series of points for a chart widget. */
public record Series(String key, String label, List<Point> points) {
  public Series {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(label, "label");
    points = points == null ? List.of() : List.copyOf(points);
  }
}
