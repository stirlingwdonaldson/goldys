package com.goldys.platform.forecasting;

import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import java.util.Comparator;
import java.util.List;

/**
 * Which catalogue metrics can be fed to TimesFM. Forecasting targets a single daily time series, so
 * the ranked-list metric ({@link MetricId#PRODUCT_TOP_SELLERS}) is excluded; everything else is
 * forecastable today. The list is sorted by id so the API returns a stable order.
 */
public final class ForecastableMetrics {

  private ForecastableMetrics() {}

  /** Whether a metric can be forecast: a single daily series rather than a ranked list. */
  public static boolean forecastable(MetricId id) {
    return id != MetricId.PRODUCT_TOP_SELLERS;
  }

  /** Every forecastable metric in the catalogue, sorted by id. */
  public static List<MetricId> forecastable(MetricCatalog catalog) {
    return catalog.ids().stream()
        .filter(ForecastableMetrics::forecastable)
        .sorted(Comparator.comparing(MetricId::value))
        .toList();
  }
}
