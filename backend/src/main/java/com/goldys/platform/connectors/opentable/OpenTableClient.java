package com.goldys.platform.connectors.opentable;

import java.time.LocalDate;

/**
 * Browser-automation port for OpenTable GuestCenter. An interface so tests can stub the browser,
 * which cannot run in CI.
 */
public interface OpenTableClient {
  /**
   * Ensure an authenticated GuestCenter session, auto-logging in only if the stored session is
   * missing or expired.
   */
  void authenticate();

  byte[] exportReservationsCsv(LocalDate from, LocalDate to);

  /** Releases the browser connection and Playwright resources. Safe to call multiple times. */
  void close();
}
