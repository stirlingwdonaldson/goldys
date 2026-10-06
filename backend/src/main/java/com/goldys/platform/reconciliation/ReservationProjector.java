package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalReservationQuery;
import com.goldys.platform.canonical.ReservationView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the reservation read model ({@code resolved_reservation_day}) from canonical
 * reservations and manual overrides.
 *
 * <p>Reservations are single-source (OpenTable), so there is no multi-source conflict; the
 * projector aggregates current facts into day × service-period counts and applies any manual
 * override. Ratios (avg party size, no-show rate, conversion) are left to the semantic layer.
 */
@Service
public class ReservationProjector {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalReservationQuery reservations;
  private final ReservationOverrideRepository overrides;
  private final ResolvedReservationDayRepository resolved;
  private final ZoneId zone;
  private final LocalTime lunchCutoff;

  public ReservationProjector(
      CanonicalReservationQuery reservations,
      ReservationOverrideRepository overrides,
      ResolvedReservationDayRepository resolved,
      @Value("${opentable.timezone:Australia/Sydney}") String zone,
      @Value("${reservations.service-period.lunch-cutoff:15:00}") String lunchCutoff) {
    this.reservations = reservations;
    this.overrides = overrides;
    this.resolved = resolved;
    this.zone = ZoneId.of(zone);
    this.lunchCutoff = LocalTime.parse(lunchCutoff);
  }

  /** Rebuild the whole read model from canonical + overrides. Used on deploy backfill. */
  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (ReservationView v : reservations.currentReservations()) {
      dates.add(v.tradingDate());
    }
    project(dates);
  }

  /** Recompute just the given dates. */
  @Transactional
  public void recompute(LocalDate... dates) {
    project(Set.of(dates));
  }

  private void project(Set<LocalDate> dates) {
    if (dates.isEmpty()) {
      return;
    }
    resolved.deleteByTradingDateIn(dates);

    Map<Key, Mutable> buckets = new HashMap<>();
    for (ReservationView v : reservations.currentReservationsForDates(dates)) {
      Key key =
          new Key(v.tradingDate(), ServicePeriod.classify(v.reservationAt(), zone, lunchCutoff));
      buckets.computeIfAbsent(key, k -> new Mutable()).add(v);
    }

    Map<Key, Long> overrideByKey = new HashMap<>();
    for (ReservationOverride o : overrides.findAllCurrent()) {
      if (o.overriddenCovers() != null) {
        overrideByKey.put(new Key(o.tradingDate(), o.servicePeriod()), o.overriddenCovers());
      }
    }

    Instant now = CLOCK.instant();
    List<ResolvedReservationDay> rows = new ArrayList<>();
    for (Map.Entry<Key, Mutable> e : buckets.entrySet()) {
      Key key = e.getKey();
      Mutable m = e.getValue();
      Long overridden = overrideByKey.get(key);
      if (overridden != null) {
        rows.add(
            new ResolvedReservationDay(
                key.date(),
                key.period(),
                m.bookings,
                m.attended,
                overridden,
                m.cancelled,
                m.noShows,
                m.walkIns,
                "override",
                "manual",
                false,
                now));
      } else {
        rows.add(
            new ResolvedReservationDay(
                key.date(),
                key.period(),
                m.bookings,
                m.attended,
                m.covers,
                m.cancelled,
                m.noShows,
                m.walkIns,
                "single",
                "OPENTABLE",
                false,
                now));
      }
    }
    resolved.saveAll(rows);
  }

  private record Key(LocalDate date, String period) {}

  private static final class Mutable {
    long bookings;
    long attended;
    long covers;
    long cancelled;
    long noShows;
    long walkIns;

    void add(ReservationView v) {
      bookings++;
      switch (v.status()) {
        case "SEATED", "COMPLETED" -> {
          attended++;
          covers += v.partySize();
        }
        case "WALK_IN" -> {
          attended++;
          covers += v.partySize();
          walkIns++;
        }
        case "CANCELLED" -> cancelled++;
        case "NO_SHOW" -> noShows++;
        default -> {
          // Unknown status counts only as a booking, never as attended/covers/no-show/cancelled.
        }
      }
    }
  }
}
