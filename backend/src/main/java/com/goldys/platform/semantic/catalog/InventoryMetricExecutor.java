package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.InventoryMetric;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for inventory metrics, over the resolved inventory projection. */
@Component
public class InventoryMetricExecutor implements MetricExecutor {
  private final InventoryMetricsQuery inventory;
  private final MetricCatalog catalog;

  public InventoryMetricExecutor(InventoryMetricsQuery inventory, MetricCatalog catalog) {
    this.inventory = inventory;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(
        MetricId.INVENTORY_PURCHASES, MetricId.INVENTORY_WASTAGE, MetricId.INVENTORY_STOCK_ON_HAND);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Function<InventoryMetric, BigDecimal> pick =
        switch (query.metric()) {
          case INVENTORY_PURCHASES -> InventoryMetric::purchases;
          case INVENTORY_WASTAGE -> InventoryMetric::wastage;
          case INVENTORY_STOCK_ON_HAND -> InventoryMetric::stockOnHand;
          default ->
              throw new IllegalArgumentException("Not an inventory metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (InventoryMetric m : inventory.dailyInventory(query.range().from(), query.range().to())) {
      byDay.put(m.date(), pick.apply(m));
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
