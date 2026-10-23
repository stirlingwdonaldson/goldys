package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.FoodBevSplit;
import com.goldys.platform.semantic.ProductCategoryMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for the food/beverage sales split, over the resolved product-sales projection. */
@Component
public class FoodBevMetricExecutor implements MetricExecutor {
  private final ProductCategoryMetricsQuery productCategory;
  private final MetricCatalog catalog;

  public FoodBevMetricExecutor(ProductCategoryMetricsQuery productCategory, MetricCatalog catalog) {
    this.productCategory = productCategory;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.SALES_FOOD, MetricId.SALES_BEV);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Function<FoodBevSplit, BigDecimal> pick =
        switch (query.metric()) {
          case SALES_FOOD -> FoodBevSplit::food;
          case SALES_BEV -> FoodBevSplit::bev;
          default -> throw new IllegalArgumentException("Not a food/bev metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (FoodBevSplit split :
        productCategory.dailyFoodBev(query.range().from(), query.range().to())) {
      byDay.put(split.date(), pick.apply(split));
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
                null,
                bucket.missingDays(),
                definition.version())
            .withCoverage(bucket.coverage());

    return new TimeSeriesResult(
        query.metric(),
        List.of(new MetricSeries(null, bucket.points())),
        bucket.coverage().notices(),
        provenance);
  }
}
