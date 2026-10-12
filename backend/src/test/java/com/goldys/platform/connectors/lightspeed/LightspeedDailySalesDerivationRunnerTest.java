package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.CanonicalPaymentQuery;
import com.goldys.platform.canonical.CanonicalPaymentQuery.PaymentDailyTotal;
import com.goldys.platform.canonical.DailySalesInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LightspeedDailySalesDerivationRunnerTest {

  private static final UUID RAW = UUID.randomUUID();

  @Test
  void derivesGstAtOneEleventh() {
    PaymentDailyTotal total =
        new PaymentDailyTotal(LocalDate.parse("2026-01-05"), new BigDecimal("110.00"), RAW);

    DailySalesInput input = LightspeedDailySalesDerivationRunner.derive(total);

    assertThat(input.sourceSystem()).isEqualTo("LIGHTSPEED_PAYMENTS");
    assertThat(input.tradingDate()).isEqualTo(LocalDate.parse("2026-01-05"));
    assertThat(input.totalSales()).isEqualByComparingTo("110.00");
    assertThat(input.gstTotal()).isEqualByComparingTo("10.00");
    assertThat(input.netTotal()).isEqualByComparingTo("100.00");
    assertThat(input.rawRecordId()).isEqualTo(RAW);
  }

  @Test
  void recordsDailySalesForEachDerivedDate() {
    CanonicalPaymentQuery query = mock(CanonicalPaymentQuery.class);
    CanonicalDailySalesIngest ingest = mock(CanonicalDailySalesIngest.class);
    LocalDate from = LocalDate.parse("2026-01-01");
    LocalDate to = LocalDate.parse("2026-03-31");
    PaymentDailyTotal a =
        new PaymentDailyTotal(LocalDate.parse("2026-01-05"), new BigDecimal("110.00"), RAW);
    PaymentDailyTotal b =
        new PaymentDailyTotal(LocalDate.parse("2026-01-06"), new BigDecimal("55.00"), RAW);
    when(query.dailyTotals(from, to)).thenReturn(List.of(a, b));

    new LightspeedDailySalesDerivationRunner(query, ingest).run(null);

    ArgumentCaptor<DailySalesInput> captor = ArgumentCaptor.forClass(DailySalesInput.class);
    verify(ingest, times(2)).record(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(DailySalesInput::tradingDate)
        .containsExactly(LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-06"));
  }

  @Test
  void skipsADateWithZeroPayments() {
    CanonicalPaymentQuery query = mock(CanonicalPaymentQuery.class);
    CanonicalDailySalesIngest ingest = mock(CanonicalDailySalesIngest.class);
    when(query.dailyTotals(any(), any())).thenReturn(List.of());

    new LightspeedDailySalesDerivationRunner(query, ingest).run(null);

    verify(ingest, never()).record(any());
  }
}
