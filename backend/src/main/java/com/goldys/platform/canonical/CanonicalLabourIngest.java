package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical labour facts, so connectors can record parsed facts. */
@Service
public class CanonicalLabourIngest {
  private final CanonicalLabourEntryService service;

  public CanonicalLabourIngest(CanonicalLabourEntryService service) {
    this.service = service;
  }

  public void synchronize(
      String source,
      java.time.LocalDate from,
      java.time.LocalDate to,
      java.util.List<LabourInput> inputs) {
    service.synchronize(source, from, to, inputs);
  }

  public void record(LabourInput input) {
    service.record(input);
  }
}
