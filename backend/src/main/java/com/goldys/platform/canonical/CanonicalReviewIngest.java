package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical reviews, so connectors can record parsed facts. */
@Service
public class CanonicalReviewIngest {
  private final CanonicalReviewService service;

  public CanonicalReviewIngest(CanonicalReviewService service) {
    this.service = service;
  }

  public void record(ReviewInput input) {
    service.record(input);
  }
}
