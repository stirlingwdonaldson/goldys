package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The documented contract for a metric: what it means, where it comes from, and how to query it.
 */
public record MetricDefinition(
    MetricId id,
    String name,
    String definition,
    String formula,
    String unit,
    String sourceDomain,
    Set<Dimension> validDimensions,
    Set<TimeGrain> allowedGrains,
    String requiredPermission,
    List<String> notes,
    String version) {
  public MetricDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(formula, "formula");
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceDomain, "sourceDomain");
    validDimensions = validDimensions == null ? Set.of() : Set.copyOf(validDimensions);
    allowedGrains = allowedGrains == null ? Set.of() : Set.copyOf(allowedGrains);
    Objects.requireNonNull(requiredPermission, "requiredPermission");
    notes = notes == null ? List.of() : List.copyOf(notes);
    Objects.requireNonNull(version, "version");
  }
}
