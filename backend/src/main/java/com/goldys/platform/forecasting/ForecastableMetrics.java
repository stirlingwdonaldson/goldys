package com.goldys.platform.forecasting;

import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import java.util.Comparator;
import java.util.List;

/**
 * Which catalogue metrics can be fed to TimesFM. Forecasting targets a single daily time series, so
 * the current runtime models net revenue with a food/beverage split. Other targets cannot be
 * substituted without changing its units and business meaning. The list is sorted by id so the API
 * returns a stable order.
 */
public final class ForecastableMetrics {

  private ForecastableMetrics() {}

  /** Whether the runtime supports the metric as its net-revenue target. */
  public static boolean forecastable(MetricId id) {
    return id == MetricId.SALES_NET;
  }

  /** Every forecastable metric in the catalogue, sorted by id. */
  public static List<MetricId> forecastable(MetricCatalog catalog) {
    return catalog.ids().stream()
        .filter(ForecastableMetrics::forecastable)
        .sorted(Comparator.comparing(MetricId::value))
        .toList();
  }
}
