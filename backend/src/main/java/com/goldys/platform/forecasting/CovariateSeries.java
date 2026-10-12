package com.goldys.platform.forecasting;

import com.goldys.platform.semantic.catalog.MetricPoint;
import java.util.List;

/**
 * A named covariate input for a forecast: the metric identifier plus the raw {@link MetricPoint}
 * history, before it is aligned onto the daily date axis. Alignment happens in {@link
 * ForecastSeriesAssembler#request}, so callers pass points as the semantic layer yields them.
 */
public record CovariateSeries(String name, List<MetricPoint> points) {}
