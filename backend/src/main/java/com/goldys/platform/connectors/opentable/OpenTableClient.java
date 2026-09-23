package com.goldys.platform.connectors.opentable;

import java.time.LocalDate;

/**
 * Browser-automation port for OpenTable GuestCenter. An interface so tests can stub the browser,
 * which cannot run in CI.
 */
public interface OpenTableClient {
  void login(String email, String password);

  byte[] exportReservationsCsv(LocalDate from, LocalDate to);

  /** Releases the underlying browser and Playwright resources. Safe to call multiple times. */
  void close();
}
