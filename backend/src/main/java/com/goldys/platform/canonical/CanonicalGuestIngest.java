package com.goldys.platform.canonical;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Public write facade for canonical guests, so connectors (and the review ingest path) can ensure a
 * minimal guest row exists for a source Guest ID before linking facts to it.
 */
@Service
public class CanonicalGuestIngest {
  private final CanonicalGuestService service;

  public CanonicalGuestIngest(CanonicalGuestService service) {
    this.service = service;
  }

  public void ensure(String guestId, String guestName, UUID rawRecordId) {
    service.ensure(guestId, guestName, rawRecordId);
  }
}
