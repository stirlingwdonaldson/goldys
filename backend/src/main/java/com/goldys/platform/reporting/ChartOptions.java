package com.goldys.platform.reporting;

import com.goldys.platform.widget.ChartKind;
import java.util.Objects;

/** Optional presentation for an analysis. All data still comes from its authorized queries. */
public record ChartOptions(
    ChartKind kind, boolean stacked, boolean horizontal, Alignment alignment) {
  public enum Alignment {
    DATE,
    WEEKDAY,
    PERIOD
  }

  public ChartOptions {
    Objects.requireNonNull(kind, "kind");
    alignment = alignment == null ? Alignment.DATE : alignment;
    if (stacked && kind != ChartKind.AREA && kind != ChartKind.BAR)
      throw new IllegalArgumentException("Only area and bar charts support stacking");
    if (horizontal && kind != ChartKind.BAR)
      throw new IllegalArgumentException("Only bar charts support horizontal orientation");
  }
}
