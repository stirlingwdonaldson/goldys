package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalPaymentQuery.PaymentDailyTotal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalPaymentQueryTest {

  @Test
  void dailyTotalsSumsAmountPerDateAndKeepsARepresentativeRawRecord() {
    CanonicalPaymentRepository repository = mock(CanonicalPaymentRepository.class);
    CanonicalPaymentQuery query = new CanonicalPaymentQuery(repository);
    LocalDate d1 = LocalDate.parse("2026-01-05");
    LocalDate d2 = LocalDate.parse("2026-01-06");
    UUID raw = UUID.randomUUID();

    CanonicalPayment p1 = payment(d1, "10.00", raw);
    CanonicalPayment p2 = payment(d1, "5.00", raw);
    CanonicalPayment p3 = payment(d2, "7.50", raw);
    when(repository.findCurrentByTradingDateBetween(any(), any())).thenReturn(List.of(p1, p2, p3));

    List<PaymentDailyTotal> totals = query.dailyTotals(d1, d2);

    assertThat(totals).hasSize(2);
    assertThat(totals.get(0).tradingDate()).isEqualTo(d1);
    assertThat(totals.get(0).totalAmount()).isEqualByComparingTo("15.00");
    assertThat(totals.get(0).rawRecordId()).isEqualTo(raw);
    assertThat(totals.get(1).tradingDate()).isEqualTo(d2);
    assertThat(totals.get(1).totalAmount()).isEqualByComparingTo("7.50");
  }

  private static CanonicalPayment payment(LocalDate date, String amount, UUID raw) {
    CanonicalPayment p = mock(CanonicalPayment.class);
    when(p.tradingDate()).thenReturn(date);
    when(p.amount()).thenReturn(new BigDecimal(amount));
    when(p.rawRecordId()).thenReturn(raw);
    return p;
  }
}
