package com.goldys.platform.widget;

import java.math.BigDecimal;
import java.util.Objects;

/** One point in a chart series: a category/date x and a numeric (nullable) y. */
public record Point(String x, BigDecimal y) {
  public Point {
    Objects.requireNonNull(x, "x");
  }
}
