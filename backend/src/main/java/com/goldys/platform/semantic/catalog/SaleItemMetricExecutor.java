package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.SaleItemMetricsQuery;
import com.goldys.platform.semantic.SaleItemMix;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for sale-item metrics, over the resolved sale-item projection. */
@Component
public class SaleItemMetricExecutor implements MetricExecutor {
  private final SaleItemMetricsQuery saleItems;
  private final MetricCatalog catalog;

  public SaleItemMetricExecutor(SaleItemMetricsQuery saleItems, MetricCatalog catalog) {
    this.saleItems = saleItems;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.SALE_ITEMS_AMOUNT, MetricId.SALE_ITEMS_QUANTITY);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Function<SaleItemMix, BigDecimal> pick =
        switch (query.metric()) {
          case SALE_ITEMS_AMOUNT -> SaleItemMix::amount;
          case SALE_ITEMS_QUANTITY -> SaleItemMix::quantity;
          default ->
              throw new IllegalArgumentException("Not a sale-item metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (SaleItemMix m : saleItems.dailyByCategory(query.range().from(), query.range().to())) {
      byDay.merge(m.tradingDate(), pick.apply(m), BigDecimal::add);
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
