package com.goldys.platform.semantic;

import com.goldys.platform.semantic.catalog.MetricCoverage;
import java.util.List;

/** Known revenue subtotals and evidence qualifying their ordering, including empty results. */
public record ProductRanking(List<TopSeller> items, MetricCoverage coverage, List<String> notices) {
  public ProductRanking {
    items = List.copyOf(items);
    notices = List.copyOf(notices);
  }
}
