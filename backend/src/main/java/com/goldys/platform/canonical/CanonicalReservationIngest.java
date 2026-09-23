package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical reservations, so connectors can record parsed facts. */
@Service
public class CanonicalReservationIngest {
  private final CanonicalReservationService service;

  public CanonicalReservationIngest(CanonicalReservationService service) {
    this.service = service;
  }

  public void record(ReservationInput input) {
    service.record(input);
  }
}
