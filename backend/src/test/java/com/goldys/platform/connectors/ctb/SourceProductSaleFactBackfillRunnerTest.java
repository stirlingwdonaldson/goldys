package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.SourceProductSaleFactIngest;
import com.goldys.platform.canonical.SourceProductSaleFactInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SourceProductSaleFactBackfillRunnerTest {

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static final String SALE_ITEMS =
      "{\"data\":[{\"stockCode\":\"STK-1\",\"stockDescription\":\"Beer\",\"quantitySold\":3,"
          + "\"amount\":120.66,\"costPerUnit\":12.50,\"foodCost\":11.00,\"totalFoodCost\":250.00,"
          + "\"profit\":48.00,\"discountedAmount\":10.00,\"taxAmount\":22.73,\"unitPrice\":13.75,"
          + "\"uniqueGroupId\":\"G-1\"},"
          + "{\"stockCode\":\"\",\"stockDescription\":\"Blank code\",\"amount\":1.0}]}";

  private static final String REVENUE =
      "{\"data\":[{\"revenueId\":1,\"revenueDate\":638706240000000000,\"totalSales\":100.0}]}";

  private static final Instant T = Instant.parse("2026-09-14T04:00:00Z");

  private final RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
  private final CanonicalProductSalesQuery productSales = mock(CanonicalProductSalesQuery.class);
  private final SourceProductSaleFactIngest ingest = mock(SourceProductSaleFactIngest.class);

  private SourceProductSaleFactBackfillRunner runner() {
    return new SourceProductSaleFactBackfillRunner(
        rawLedger, new CtbSaleItemParser(), ingest, productSales);
  }

  private void refs(RawLedgerQuery.RawRecordRef... refs) {
    when(rawLedger.rawRecordRefsForFetcher("CTB", "ctb-revenue")).thenReturn(List.of(refs));
  }

  @SuppressWarnings("unchecked")
  private List<SourceProductSaleFactInput> recorded() {
    ArgumentCaptor<List<SourceProductSaleFactInput>> captor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(ingest).recordAll(captor.capture());
    return captor.getValue();
  }

  @Test
  void replaysDatedRecordsAndRejectsBlankCodeRows() {
    UUID id = UUID.randomUUID();
    refs(new RawLedgerQuery.RawRecordRef(id, "sale-items:2026-09-14", T));
    when(rawLedger.payloadBytes(id)).thenReturn(bytes(SALE_ITEMS));

    SourceProductSaleFactBackfillRunner.Result result = runner().replay();

    List<SourceProductSaleFactInput> inputs = recorded();
    assertThat(inputs).hasSize(1); // the blank-stock-code row is rejected

    SourceProductSaleFactInput in = inputs.get(0);
    assertThat(in.sourceRecordRef()).isEqualTo("STK-1@2026-09-14@G-1");
    assertThat(in.tradingDate()).isEqualTo(LocalDate.parse("2026-09-14"));
    assertThat(in.stockCode()).isEqualTo("STK-1");
    assertThat(in.costPerUnit()).isEqualByComparingTo("12.50");
    assertThat(in.foodCost()).isEqualByComparingTo("11.00");
    assertThat(in.totalFoodCost()).isEqualByComparingTo("250.00");
    assertThat(in.profit()).isEqualByComparingTo("48.00");
    assertThat(in.discountedAmount()).isEqualByComparingTo("10.00");
    assertThat(in.taxAmount()).isEqualByComparingTo("22.73");
    assertThat(in.unitPrice()).isEqualByComparingTo("13.75");
    assertThat(in.uniqueGroupId()).isEqualTo("G-1");
    assertThat(in.rawRecordId()).isEqualTo(id);

    assertThat(result.processed()).isEqualTo(1);
    assertThat(result.accepted()).isEqualTo(1);
    assertThat(result.rejected()).isEqualTo(1); // the blank-code row
    assertThat(result.ambiguous()).isZero();
    assertThat(result.skipped()).isZero();
  }

  @Test
  void recoversDateFromCanonicalProvenanceForUndatedRecords() {
    UUID id = UUID.randomUUID();
    refs(new RawLedgerQuery.RawRecordRef(id, null, T));
    when(rawLedger.payloadBytes(id)).thenReturn(bytes(SALE_ITEMS));
    when(productSales.tradingDatesForRawRecord(id))
        .thenReturn(List.of(LocalDate.parse("2026-09-14")));

    runner().replay();

    assertThat(recorded().get(0).tradingDate()).isEqualTo(LocalDate.parse("2026-09-14"));
  }

  @Test
  void treatsUndatedRecordWithoutCanonicalDateAsAmbiguous() {
    UUID id = UUID.randomUUID();
    refs(new RawLedgerQuery.RawRecordRef(id, null, T));
    when(rawLedger.payloadBytes(id)).thenReturn(bytes(SALE_ITEMS));
    when(productSales.tradingDatesForRawRecord(id)).thenReturn(List.of());

    SourceProductSaleFactBackfillRunner.Result result = runner().replay();

    verify(ingest, never()).recordAll(any());
    assertThat(result.ambiguous()).isEqualTo(1);
  }

  @Test
  void treatsUndatedRecordWithMultipleCanonicalDatesAsAmbiguous() {
    UUID id = UUID.randomUUID();
    refs(new RawLedgerQuery.RawRecordRef(id, null, T));
    when(rawLedger.payloadBytes(id)).thenReturn(bytes(SALE_ITEMS));
    when(productSales.tradingDatesForRawRecord(id))
        .thenReturn(List.of(LocalDate.parse("2026-09-14"), LocalDate.parse("2026-09-15")));

    SourceProductSaleFactBackfillRunner.Result result = runner().replay();

    verify(ingest, never()).recordAll(any());
    assertThat(result.ambiguous()).isEqualTo(1);
  }

  @Test
  void skipsRevenuePages() {
    UUID id = UUID.randomUUID();
    refs(new RawLedgerQuery.RawRecordRef(id, null, T));
    when(rawLedger.payloadBytes(id)).thenReturn(bytes(REVENUE));

    SourceProductSaleFactBackfillRunner.Result result = runner().replay();

    verify(ingest, never()).recordAll(any());
    verify(productSales, never()).tradingDatesForRawRecord(any());
    assertThat(result.skipped()).isEqualTo(1);
  }

  @Test
  void rejectsUnparseableDay() {
    UUID id = UUID.randomUUID();
    refs(new RawLedgerQuery.RawRecordRef(id, "sale-items:not-a-date", T));

    SourceProductSaleFactBackfillRunner.Result result = runner().replay();

    verify(ingest, never()).recordAll(any());
    assertThat(result.rejected()).isEqualTo(1);
  }
}
