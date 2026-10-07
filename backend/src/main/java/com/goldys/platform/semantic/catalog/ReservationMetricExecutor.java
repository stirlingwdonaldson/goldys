package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.ReservationSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for reservation metrics, over the resolved reservation projection. */
@Component
public class ReservationMetricExecutor implements MetricExecutor {
  private final ReservationMetricsQuery reservations;
  private final MetricCatalog catalog;

  public ReservationMetricExecutor(ReservationMetricsQuery reservations, MetricCatalog catalog) {
    this.reservations = reservations;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(
        MetricId.RESERVATIONS_BOOKINGS,
        MetricId.RESERVATIONS_ATTENDED,
        MetricId.RESERVATIONS_COVERS,
        MetricId.RESERVATIONS_NO_SHOWS);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Function<ReservationSummary, Long> pick =
        switch (query.metric()) {
          case RESERVATIONS_BOOKINGS -> ReservationSummary::bookings;
          case RESERVATIONS_ATTENDED -> ReservationSummary::attended;
          case RESERVATIONS_COVERS -> ReservationSummary::covers;
          case RESERVATIONS_NO_SHOWS -> ReservationSummary::noShows;
          default ->
              throw new IllegalArgumentException("Not a reservation metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (ReservationSummary s :
        reservations.dailySummaries(query.range().from(), query.range().to())) {
      byDay.put(s.date(), BigDecimal.valueOf(pick.apply(s)));
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
