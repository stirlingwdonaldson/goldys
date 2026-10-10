package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.DeletedSaleDay;
import com.goldys.platform.semantic.DeletedSaleMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Base executor for deleted-sales metrics, over the resolved deleted-sale projection. */
@Component
public class DeletedSaleMetricExecutor implements MetricExecutor {
  private final DeletedSaleMetricsQuery deletedSales;
  private final MetricCatalog catalog;

  public DeletedSaleMetricExecutor(DeletedSaleMetricsQuery deletedSales, MetricCatalog catalog) {
    this.deletedSales = deletedSales;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.DELETED_SALES_AMOUNT);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (DeletedSaleDay d : deletedSales.dailyTotals(query.range().from(), query.range().to())) {
      byDay.merge(d.tradingDate(), d.totalIncTax(), BigDecimal::add);
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
