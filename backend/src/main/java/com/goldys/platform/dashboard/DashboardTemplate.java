package com.goldys.platform.dashboard;

import java.util.List;
import java.util.Objects;

/**
 * A named starting-point dashboard built from catalogue {@code MetricId}s. Templates share the
 * ordinary {@link SavedWidget}/{@link DashboardDocument} shape, so instantiating one routes through
 * the normal create path — never a separate rendering path.
 */
public record DashboardTemplate(
    String id, String name, String description, List<SavedWidget> widgets) {
  public DashboardTemplate {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    widgets = widgets == null ? List.of() : List.copyOf(widgets);
  }
}
