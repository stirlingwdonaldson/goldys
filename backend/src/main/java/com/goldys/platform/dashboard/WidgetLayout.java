package com.goldys.platform.dashboard;

/** A widget's grid span: width in 12-column units, height in row units. */
public record WidgetLayout(int w, int h) {
  public WidgetLayout {
    if (w < 1 || w > 12) throw new IllegalArgumentException("widget width out of range: " + w);
    if (h < 1 || h > 4) throw new IllegalArgumentException("widget height out of range: " + h);
  }
}
