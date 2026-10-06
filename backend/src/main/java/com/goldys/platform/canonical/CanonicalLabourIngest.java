package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical labour facts, so connectors can record parsed facts. */
@Service
public class CanonicalLabourIngest {
  private final CanonicalLabourEntryService service;

  public CanonicalLabourIngest(CanonicalLabourEntryService service) {
    this.service = service;
  }

  public void record(LabourInput input) {
    service.record(input);
  }
}
