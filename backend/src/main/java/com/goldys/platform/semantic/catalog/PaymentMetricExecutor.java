package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.PaymentMetricsQuery;
import com.goldys.platform.semantic.PaymentMix;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for payment metrics, over the resolved payment projection. */
@Component
public class PaymentMetricExecutor implements MetricExecutor {
  private final PaymentMetricsQuery payments;
  private final MetricCatalog catalog;

  public PaymentMetricExecutor(PaymentMetricsQuery payments, MetricCatalog catalog) {
    this.payments = payments;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.PAYMENTS_AMOUNT, MetricId.PAYMENTS_TIP);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Function<PaymentMix, BigDecimal> pick =
        switch (query.metric()) {
          case PAYMENTS_AMOUNT -> PaymentMix::amount;
          case PAYMENTS_TIP -> PaymentMix::tip;
          default -> throw new IllegalArgumentException("Not a payment metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (PaymentMix m : payments.dailyMix(query.range().from(), query.range().to())) {
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
