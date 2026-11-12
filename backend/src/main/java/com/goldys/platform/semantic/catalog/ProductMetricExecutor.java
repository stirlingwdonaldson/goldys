package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductSalesMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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

    List<ProductSalesMetric> rows = product.productSales(query.range().from(), query.range().to());
    Map<LocalDate, BigDecimal> byDay = daily(rows, pick);
    List<MetricSeries> series = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    if (query.dimensions().contains(Dimension.PRODUCT)) {
      Map<String, List<ProductSalesMetric>> groups = new LinkedHashMap<>();
      for (ProductSalesMetric row : rows) {
        groups.computeIfAbsent(row.productName(), ignored -> new ArrayList<>()).add(row);
      }
      for (var group : groups.entrySet()) {
        Map<LocalDate, BigDecimal> values = daily(group.getValue(), pick);
        var bucket =
            GrainAggregator.sum(values, query.range().from(), query.range().to(), query.grain());
        series.add(new MetricSeries(group.getKey(), bucket.points()));
        bucket.coverage().notices().forEach(note -> notices.add(group.getKey() + ": " + note));
        // A missing product observation must not look complete because another product was
        // observed.
        for (LocalDate missing : bucket.missingDays()) {
          if (byDay.containsKey(missing)) byDay.put(missing, null);
        }
      }
      notices.add(
          "Product identity is source-normalized name; absent product/day observations are not zero.");
    }
    GrainAggregator.Bucket bucket =
        GrainAggregator.sum(byDay, query.range().from(), query.range().to(), query.grain());
    if (series.isEmpty()) series.add(new MetricSeries(null, bucket.points()));
    notices.addAll(bucket.coverage().notices());
    MetricDefinition definition = catalog.definition(query.metric());
    MetricProvenance provenance =
        new MetricProvenance(
                query.metric(),
                definition.version(),
                query.range(),
                query.grain(),
                definition.sourceDomain(),
                null,
                bucket.missingDays(),
                definition.version())
            .withCoverage(bucket.coverage());
    return new TimeSeriesResult(
        query.metric(), List.copyOf(series), List.copyOf(notices), provenance);
  }

  private Map<LocalDate, BigDecimal> daily(
      List<ProductSalesMetric> rows, Function<ProductSalesMetric, BigDecimal> pick) {
    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    Map<LocalDate, Boolean> unresolved = new HashMap<>();
    for (ProductSalesMetric m : rows) {
      BigDecimal v = pick.apply(m);
      if (v == null) {
        unresolved.put(m.tradingDate(), true);
        byDay.put(m.tradingDate(), null);
      } else if (!unresolved.getOrDefault(m.tradingDate(), false)) {
        byDay.merge(m.tradingDate(), v, BigDecimal::add);
      }
    }
    return byDay;
  }
}
