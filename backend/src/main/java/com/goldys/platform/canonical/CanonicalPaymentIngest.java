package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical payments, so connectors can record parsed facts. */
@Service
public class CanonicalPaymentIngest {
  private final CanonicalPaymentService service;

  public CanonicalPaymentIngest(CanonicalPaymentService service) {
    this.service = service;
  }

  public void record(PaymentInput input) {
    service.record(input);
  }
}
