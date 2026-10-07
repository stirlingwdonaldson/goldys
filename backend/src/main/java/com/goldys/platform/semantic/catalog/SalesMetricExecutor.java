package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for the sales metrics, over the resolved daily-sales projection. */
@Component
public class SalesMetricExecutor implements MetricExecutor {
  private final SalesMetricsQuery sales;
  private final MetricCatalog catalog;

  public SalesMetricExecutor(SalesMetricsQuery sales, MetricCatalog catalog) {
    this.sales = sales;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.SALES_GROSS, MetricId.SALES_NET, MetricId.SALES_GST);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Function<DailySalesMetric, BigDecimal> pick =
        switch (query.metric()) {
          case SALES_GROSS -> DailySalesMetric::grossSales;
          case SALES_NET -> DailySalesMetric::netSales;
          case SALES_GST -> DailySalesMetric::gst;
          default -> throw new IllegalArgumentException("Not a sales metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (DailySalesMetric m : sales.dailySales(query.range().from(), query.range().to())) {
      byDay.put(m.tradingDate(), pick.apply(m));
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
            // TODO(provenance): plumb max(resolved_at) from the *MetricsQuery read, not
            // Instant.EPOCH
            Instant.EPOCH,
            bucket.missingDays(),
            definition.version());

    return new TimeSeriesResult(
        query.metric(),
        List.of(new MetricSeries(null, bucket.points())),
        notices(bucket.missingDays()),
        provenance);
  }

  static List<String> notices(List<LocalDate> missingDays) {
    return missingDays.isEmpty() ? List.of() : List.of(missingDays.size() + " day(s) unresolved");
  }
}
