package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.CanonicalPaymentQuery;
import com.goldys.platform.canonical.CanonicalPaymentQuery.PaymentDailyTotal;
import com.goldys.platform.canonical.DailySalesInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off derivation: fill daily-sales totals for a window the Lightspeed daily report never
 * covered (2026 Q1) by summing the already-canonicalized Lightspeed payment amounts per trading
 * date. GST is estimated at the standard Australian 1/11 (10% inclusive); net is total minus GST.
 *
 * <p>Recorded under the {@code LIGHTSPEED_PAYMENTS} source so the resolved view keeps the derived
 * provenance distinct from a genuine {@code LIGHTSPEED} daily report. Idempotent: re-running is a
 * no-op once the same value is current. Gated on {@code
 * app.lightspeed-daily-sales-derivation=true}, off by default; run it after the payments replay has
 * populated {@code canonical_payment}.
 *
 * <p>Ordered one step below the other Lightspeed backfill runners (which replay payments at {@code
 * HIGHEST_PRECEDENCE}) so the derivation reads a complete {@code canonical_payment} even if both
 * flags are enabled in a single deploy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(name = "app.lightspeed-daily-sales-derivation", havingValue = "true")
public class LightspeedDailySalesDerivationRunner implements ApplicationRunner {
  static final String SOURCE = "LIGHTSPEED_PAYMENTS";
  private static final BigDecimal GST_DIVISOR = new BigDecimal("11");
  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 3, 31);

  private final CanonicalPaymentQuery payments;
  private final CanonicalDailySalesIngest dailySales;

  public LightspeedDailySalesDerivationRunner(
      CanonicalPaymentQuery payments, CanonicalDailySalesIngest dailySales) {
    this.payments = payments;
    this.dailySales = dailySales;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (PaymentDailyTotal total : payments.dailyTotals(FROM, TO)) {
      dailySales.record(derive(total));
    }
  }

  static DailySalesInput derive(PaymentDailyTotal total) {
    BigDecimal gst = total.totalAmount().divide(GST_DIVISOR, 4, RoundingMode.HALF_UP);
    BigDecimal net = total.totalAmount().subtract(gst);
    return new DailySalesInput(
        SOURCE, total.tradingDate(), total.totalAmount(), gst, net, total.rawRecordId());
  }
}
