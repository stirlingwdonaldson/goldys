package com.goldys.platform.dashboard;

import com.goldys.platform.semantic.catalog.Comparison;
import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.util.Set;

/** Dashboard-level reusable filters merged into each widget's query at render. */
public record DashboardFilters(
    TimeRange dateRange, Comparison comparison, Set<Dimension> dimensions) {
  public DashboardFilters {
    dimensions = dimensions == null ? Set.of() : Set.copyOf(dimensions);
  }

  public static DashboardFilters empty() {
    return new DashboardFilters(null, null, Set.of());
  }
}
