package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical payments, so modules outside this package never touch the
 * package-private entity or repository directly.
 */
@Service
public class CanonicalPaymentQuery {
  private final CanonicalPaymentRepository repository;

  public CanonicalPaymentQuery(CanonicalPaymentRepository repository) {
    this.repository = repository;
  }

  /** All current payments, mapped to views. */
  public List<PaymentView> currentPayments() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  /** Current payments whose trading date falls in {@code dates}. */
  public List<PaymentView> currentPaymentsForDates(Collection<LocalDate> dates) {
    return currentPayments().stream().filter(v -> dates.contains(v.tradingDate())).toList();
  }

  private PaymentView toView(CanonicalPayment p) {
    return new PaymentView(
        p.tradingDate(), p.paymentTypeName(), p.amount(), p.tip(), p.paymentCount());
  }
}
