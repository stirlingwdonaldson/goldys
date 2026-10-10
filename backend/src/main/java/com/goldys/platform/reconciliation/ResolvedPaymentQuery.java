package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.PaymentMetricsQuery;
import com.goldys.platform.semantic.PaymentMix;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link PaymentMetricsQuery} backed by the resolved payment projection. */
@Service
public class ResolvedPaymentQuery implements PaymentMetricsQuery {
  private final ResolvedPaymentDayRepository repository;

  public ResolvedPaymentQuery(ResolvedPaymentDayRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<PaymentMix> dailyMix(LocalDate from, LocalDate to) {
    return repository
        .findByTradingDateBetweenOrderByTradingDateAscPaymentTypeNameAsc(from, to)
        .stream()
        .map(
            r ->
                new PaymentMix(
                    r.tradingDate(),
                    r.paymentTypeName(),
                    r.amount(),
                    r.tip(),
                    r.paymentCount(),
                    r.hasConflict()))
        .toList();
  }
}
