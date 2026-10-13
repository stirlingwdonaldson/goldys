package com.goldys.platform.canonical;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical reservations, so modules outside this package never touch
 * the package-private entity or repository directly.
 */
@Service
public class CanonicalReservationQuery {
  private final CanonicalReservationRepository repository;
  private final ZoneId zone;

  public CanonicalReservationQuery(
      CanonicalReservationRepository repository,
      @Value("${opentable.timezone:Australia/Melbourne}") String zone) {
    this.repository = repository;
    this.zone = ZoneId.of(zone);
  }

  /** All current reservations, mapped to views. */
  public List<ReservationView> currentReservations() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  /** Current reservations whose local trading date falls in {@code dates}. */
  public List<ReservationView> currentReservationsForDates(Collection<LocalDate> dates) {
    if (dates.isEmpty()) {
      return List.of();
    }
    // Scope to the date range in the venue zone instead of loading every current reservation and
    // filtering in Java: a bulk backfill records tens of thousands of facts, and the per-record
    // projection recompute otherwise re-reads the whole table each time (quadratic).
    LocalDate min = Collections.min(dates);
    LocalDate max = Collections.max(dates);
    Instant from = min.atStartOfDay(zone).toInstant();
    Instant to = max.plusDays(1).atStartOfDay(zone).toInstant();
    return repository.findCurrentByReservationAtBetween(from, to).stream()
        .map(this::toView)
        .filter(v -> dates.contains(v.tradingDate()))
        .toList();
  }

  private ReservationView toView(CanonicalReservation r) {
    return new ReservationView(
        r.reservationAt().atZone(zone).toLocalDate(),
        r.sourceSystem(),
        r.reservationAt(),
        r.partySize(),
        r.status());
  }
}
