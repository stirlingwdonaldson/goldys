package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Objects;

/** A ranked-list metric result (used by {@code product.top_sellers}). */
public record RankedListResult(
    MetricId metric,
    List<MetricRankedItem> items,
    List<String> notices,
    MetricProvenance provenance)
    implements MetricResult {
  public RankedListResult {
    Objects.requireNonNull(metric, "metric");
    items = items == null ? List.of() : List.copyOf(items);
    notices = notices == null ? List.of() : List.copyOf(notices);
    Objects.requireNonNull(provenance, "provenance");
  }
}
