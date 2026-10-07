package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductSalesMetric;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for product-sales metrics, over the resolved product-sales projection. */
@Component
public class ProductMetricExecutor implements MetricExecutor {
  private final ProductMetricsQuery product;
  private final MetricCatalog catalog;

  public ProductMetricExecutor(ProductMetricsQuery product, MetricCatalog catalog) {
    this.product = product;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.PRODUCT_SALES_AMOUNT, MetricId.PRODUCT_SALES_QUANTITY);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Function<ProductSalesMetric, BigDecimal> pick =
        switch (query.metric()) {
          case PRODUCT_SALES_AMOUNT -> ProductSalesMetric::amount;
          case PRODUCT_SALES_QUANTITY -> ProductSalesMetric::quantitySold;
          default -> throw new IllegalArgumentException("Not a product metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    Map<LocalDate, Boolean> unresolved = new HashMap<>();
    for (ProductSalesMetric m : product.productSales(query.range().from(), query.range().to())) {
      BigDecimal v = pick.apply(m);
      if (v == null) {
        unresolved.put(m.tradingDate(), true);
        byDay.remove(m.tradingDate());
      } else if (!unresolved.getOrDefault(m.tradingDate(), false)) {
        byDay.merge(m.tradingDate(), v, BigDecimal::add);
      }
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
