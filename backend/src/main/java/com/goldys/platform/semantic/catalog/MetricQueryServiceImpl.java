package com.goldys.platform.semantic.catalog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Routes a {@link MetricQuery} to its executor after validating it against the catalogue. */
@Service
public class MetricQueryServiceImpl implements MetricQueryService {
  private final MetricCatalog catalog;
  private final Map<MetricId, MetricExecutor> executors;

  public MetricQueryServiceImpl(MetricCatalog catalog, List<MetricExecutor> executors) {
    this.catalog = catalog;
    Map<MetricId, MetricExecutor> byId = new HashMap<>();
    for (MetricExecutor executor : executors) {
      for (MetricId id : executor.ids()) {
        byId.put(id, executor);
      }
    }
    this.executors = Map.copyOf(byId);
  }

  @Override
  public MetricResult query(MetricQuery query) {
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(query.metric(), "metric");
    MetricDefinition definition = catalog.definition(query.metric());
    if (!definition.allowedGrains().contains(query.grain())) {
      throw new IllegalArgumentException(
          "grain " + query.grain() + " is not allowed for " + query.metric().value());
    }
    if (!definition.validDimensions().containsAll(query.dimensions())) {
      throw new IllegalArgumentException(
          "dimensions " + query.dimensions() + " are not allowed for " + query.metric().value());
    }
    MetricExecutor executor = executors.get(query.metric());
    if (executor == null) {
      throw new IllegalArgumentException("No executor for metric: " + query.metric());
    }
    return executor.evaluate(query);
  }
}
