package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
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
      @Value("${opentable.timezone:Australia/Sydney}") String zone) {
    this.repository = repository;
    this.zone = ZoneId.of(zone);
  }

  /** Current reservations whose local trading date falls in {@code dates}. */
  public List<ReservationView> currentReservationsForDates(Collection<LocalDate> dates) {
    return repository.findAllCurrent().stream()
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
