package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.ProductMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Ranks the groups of a dimension by a metric, for the (metric, dimension) pairs that have a
 * grouped read. Ranked-list results are the composable "rank groups" primitive; {@code
 * get_top_products} and {@code rank_dimension} are thin adapters over this.
 */
@Component
public class RankingService {
  private final ProductMetricsQuery products;
  private final LabourMetricsQuery labour;

  public RankingService(ProductMetricsQuery products, LabourMetricsQuery labour) {
    this.products = products;
    this.labour = labour;
  }

  public RankedListResult rank(MetricId metric, Dimension dimension, TimeRange range, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    if (metric == MetricId.PRODUCT_SALES_AMOUNT && dimension == Dimension.PRODUCT) {
      return productRank(range, limit);
    }
    if ((metric == MetricId.LABOUR_COST
            || metric == MetricId.LABOUR_ACTUAL_HOURS
            || metric == MetricId.LABOUR_SCHEDULED_HOURS)
        && dimension == Dimension.DEPARTMENT) {
      return labourRank(metric, range, limit);
    }
    throw new IllegalArgumentException(
        "no ranked read for metric " + metric.value() + " by " + dimension);
  }

  private RankedListResult productRank(TimeRange range, int limit) {
    List<MetricRankedItem> items =
        products.topSellers(range.from(), range.to(), limit).stream()
            .map(
                t ->
                    new MetricRankedItem(
                        t.productName(), t.amount(), t.quantitySold(), t.hasConflict()))
            .toList();
    return new RankedListResult(
        MetricId.PRODUCT_SALES_AMOUNT,
        items,
        List.of(),
        provenance(MetricId.PRODUCT_SALES_AMOUNT, range));
  }

  private RankedListResult labourRank(MetricId metric, TimeRange range, int limit) {
    Map<String, BigDecimal> totals = new LinkedHashMap<>();
    for (LabourMetric m : labour.dailyLabour(range.from(), range.to())) {
      BigDecimal v = pick(metric, m);
      if (v != null) {
        totals.merge(m.department(), v, BigDecimal::add);
      }
    }
    List<MetricRankedItem> items =
        totals.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(limit)
            .map(e -> new MetricRankedItem(e.getKey(), e.getValue(), null, false))
            .toList();
    return new RankedListResult(metric, items, List.of(), provenance(metric, range));
  }

  private static BigDecimal pick(MetricId metric, LabourMetric m) {
    return switch (metric) {
      case LABOUR_COST -> m.actualCost();
      case LABOUR_ACTUAL_HOURS -> m.actualHours();
      case LABOUR_SCHEDULED_HOURS -> m.scheduledHours();
      default -> throw new IllegalArgumentException("Not a labour metric: " + metric);
    };
  }

  private MetricProvenance provenance(MetricId metric, TimeRange range) {
    return new MetricProvenance(
        metric, "1", range, TimeGrain.DAY, "derived", Instant.EPOCH, List.of(), "1");
  }
}
