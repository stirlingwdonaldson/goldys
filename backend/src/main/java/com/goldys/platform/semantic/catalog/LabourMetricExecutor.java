package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for labour metrics, over the resolved labour projection. */
@Component
public class LabourMetricExecutor implements MetricExecutor {
  private final LabourMetricsQuery labour;
  private final MetricCatalog catalog;

  public LabourMetricExecutor(LabourMetricsQuery labour, MetricCatalog catalog) {
    this.labour = labour;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(
        MetricId.LABOUR_SCHEDULED_HOURS, MetricId.LABOUR_ACTUAL_HOURS, MetricId.LABOUR_COST);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Function<LabourMetric, BigDecimal> pick =
        switch (query.metric()) {
          case LABOUR_SCHEDULED_HOURS -> LabourMetric::scheduledHours;
          case LABOUR_ACTUAL_HOURS -> LabourMetric::actualHours;
          case LABOUR_COST -> LabourMetric::actualCost;
          default -> throw new IllegalArgumentException("Not a labour metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (LabourMetric m : labour.dailyLabour(query.range().from(), query.range().to())) {
      byDay.merge(m.date(), pick.apply(m), BigDecimal::add);
    }
    GrainAggregator.Bucket bucket =
        GrainAggregator.sum(byDay, query.range().from(), query.range().to(), query.grain());

    MetricDefinition definition = catalog.definition(query.metric());
    MetricProvenance provenance =
        new MetricProvenance(
            query.metric(),
            definition.version(),
            query.range(),
            query.grain(),
            definition.sourceDomain(),
            Instant.EPOCH,
            bucket.missingDays(),
            definition.version());

    return new TimeSeriesResult(
        query.metric(),
        List.of(new MetricSeries(null, bucket.points())),
        SalesMetricExecutor.notices(bucket.missingDays()),
        provenance);
  }
}
