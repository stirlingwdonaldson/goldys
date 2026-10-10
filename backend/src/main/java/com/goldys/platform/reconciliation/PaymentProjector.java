package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalPaymentQuery;
import com.goldys.platform.canonical.PaymentView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the payments read model ({@code resolved_payment_day}) from canonical payments.
 *
 * <p>Payments are single-source (Lightspeed), so there is no multi-source conflict; the projector
 * aggregates current facts into day × payment-type totals with resolution type {@code single}.
 */
@Service
public class PaymentProjector {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalPaymentQuery payments;
  private final ResolvedPaymentDayRepository resolved;

  public PaymentProjector(CanonicalPaymentQuery payments, ResolvedPaymentDayRepository resolved) {
    this.payments = payments;
    this.resolved = resolved;
  }

  /** Rebuild the whole read model from canonical. */
  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (PaymentView v : payments.currentPayments()) {
      dates.add(v.tradingDate());
    }
    project(dates);
  }

  /** Recompute just the given dates. */
  @Transactional
  public void recompute(LocalDate... dates) {
    project(Set.of(dates));
  }

  private void project(Set<LocalDate> dates) {
    if (dates.isEmpty()) {
      return;
    }
    resolved.deleteByTradingDateIn(dates);

    Map<Key, Mutable> buckets = new HashMap<>();
    for (PaymentView v : payments.currentPaymentsForDates(dates)) {
      Key key = new Key(v.tradingDate(), nz(v.paymentTypeName()));
      buckets.computeIfAbsent(key, k -> new Mutable()).add(v);
    }

    Instant now = CLOCK.instant();
    List<ResolvedPaymentDay> rows = new ArrayList<>();
    for (Map.Entry<Key, Mutable> e : buckets.entrySet()) {
      Key key = e.getKey();
      Mutable m = e.getValue();
      rows.add(
          new ResolvedPaymentDay(
              key.date(),
              key.type(),
              m.amount,
              m.tip,
              m.count,
              "single",
              "LIGHTSPEED",
              false,
              now));
    }
    resolved.saveAll(rows);
  }

  private static String nz(String v) {
    return v == null || v.isBlank() ? "Unspecified" : v;
  }

  private record Key(LocalDate date, String type) {}

  private static final class Mutable {
    BigDecimal amount = BigDecimal.ZERO;
    BigDecimal tip = BigDecimal.ZERO;
    long count = 0;

    void add(PaymentView v) {
      amount = amount.add(nz(v.amount()));
      tip = tip.add(nz(v.tip()));
      count += v.paymentCount();
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
