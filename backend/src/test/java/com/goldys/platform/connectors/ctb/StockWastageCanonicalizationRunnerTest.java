package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalStockCountIngest;
import com.goldys.platform.canonical.CanonicalStocktakeIngest;
import com.goldys.platform.canonical.CanonicalWastageIngest;
import com.goldys.platform.canonical.StockCountInput;
import com.goldys.platform.canonical.StocktakeInput;
import com.goldys.platform.canonical.WastageInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StockWastageCanonicalizationRunnerTest {

  private static final String HEADER_05 =
      "{\"data\":[{\"stocktakeId\":1,\"stocktakeDate\":\"2026-10-05\"}],\"message\":{\"IsSuccess\":true}}";
  private static final String HEADER_06 =
      "{\"data\":[{\"stocktakeId\":1,\"stocktakeDate\":\"2026-10-06\"}],\"message\":{\"IsSuccess\":true}}";
  private static final String DETAIL =
      "{\"data\":[{\"id\":100,\"stockDesc\":\"Oil\",\"qtyCount\":5}],\"message\":{\"IsSuccess\":true}}";
  private static final String WASTAGE =
      "{\"data\":[{\"wastageRecordId\":7,\"wastageRecordDetailId\":359,\"recordDate\":\"2026-10-05\","
          + "\"stockDesc\":\"Oil\",\"quantity\":3}],\"message\":{\"IsSuccess\":true}}";

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void replaysStocktakesAndCountsAndWastage() {
    RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
    when(rawLedger.rawPayloadsForFetcherWithFetchedAt("CTB", "ctb-stocktakes"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayloadWithRefAndTime(
                    UUID.randomUUID(),
                    bytes(HEADER_05),
                    null,
                    Instant.parse("2026-10-12T00:00:00Z"))));
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-stocktake-detail"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayloadWithRef(
                    UUID.randomUUID(), bytes(DETAIL), "stocktake:1")));
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-wastage-detail"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayloadWithRef(
                    UUID.randomUUID(), bytes(WASTAGE), "wastage:7")));

    CanonicalStocktakeIngest stocktakeIngest = mock(CanonicalStocktakeIngest.class);
    CanonicalStockCountIngest stockCountIngest = mock(CanonicalStockCountIngest.class);
    CanonicalWastageIngest wastageIngest = mock(CanonicalWastageIngest.class);

    runner(rawLedger, stocktakeIngest, stockCountIngest, wastageIngest).run(null);

    ArgumentCaptor<StocktakeInput> headerCaptor = ArgumentCaptor.forClass(StocktakeInput.class);
    verify(stocktakeIngest).record(headerCaptor.capture());
    assertThat(headerCaptor.getValue().stocktakeId()).isEqualTo(1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockCountInput>> countCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(stockCountIngest).recordAll(countCaptor.capture());
    assertThat(countCaptor.getValue()).hasSize(1);
    assertThat(countCaptor.getValue().get(0).countedDate())
        .isEqualTo(LocalDate.parse("2026-10-05"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<WastageInput>> wasteCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(wastageIngest).recordAll(wasteCaptor.capture());
    assertThat(wasteCaptor.getValue()).hasSize(1);
    assertThat(wasteCaptor.getValue().get(0).wastageRecordId()).isEqualTo(7);
  }

  @Test
  void dedupesHeadersToLatestByFetchedAt() {
    RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
    when(rawLedger.rawPayloadsForFetcherWithFetchedAt("CTB", "ctb-stocktakes"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayloadWithRefAndTime(
                    UUID.randomUUID(),
                    bytes(HEADER_05),
                    null,
                    Instant.parse("2026-10-12T00:00:00Z")),
                new RawLedgerQuery.RawPayloadWithRefAndTime(
                    UUID.randomUUID(),
                    bytes(HEADER_06),
                    null,
                    Instant.parse("2026-10-12T01:00:00Z"))));
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-stocktake-detail"))
        .thenReturn(List.of());
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-wastage-detail")).thenReturn(List.of());

    CanonicalStocktakeIngest stocktakeIngest = mock(CanonicalStocktakeIngest.class);
    CanonicalStockCountIngest stockCountIngest = mock(CanonicalStockCountIngest.class);
    CanonicalWastageIngest wastageIngest = mock(CanonicalWastageIngest.class);

    runner(rawLedger, stocktakeIngest, stockCountIngest, wastageIngest).run(null);

    ArgumentCaptor<StocktakeInput> headerCaptor = ArgumentCaptor.forClass(StocktakeInput.class);
    verify(stocktakeIngest, times(1)).record(headerCaptor.capture());
    assertThat(headerCaptor.getValue().stocktakeDate()).isEqualTo(LocalDate.parse("2026-10-06"));
  }

  @Test
  void throwsWhenCountHasNoHeader() {
    RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
    when(rawLedger.rawPayloadsForFetcherWithFetchedAt("CTB", "ctb-stocktakes"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayloadWithRefAndTime(
                    UUID.randomUUID(),
                    bytes(HEADER_05),
                    null,
                    Instant.parse("2026-10-12T00:00:00Z"))));
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-stocktake-detail"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayloadWithRef(
                    UUID.randomUUID(), bytes(DETAIL), "stocktake:999")));
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-wastage-detail")).thenReturn(List.of());

    CanonicalStocktakeIngest stocktakeIngest = mock(CanonicalStocktakeIngest.class);
    CanonicalStockCountIngest stockCountIngest = mock(CanonicalStockCountIngest.class);
    CanonicalWastageIngest wastageIngest = mock(CanonicalWastageIngest.class);

    assertThatThrownBy(
            () -> runner(rawLedger, stocktakeIngest, stockCountIngest, wastageIngest).run(null))
        .isInstanceOf(ConnectorFetchException.class);
    verify(stockCountIngest, never()).recordAll(any());
  }

  @Test
  void skipsUnparseableDetailRef() {
    RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
    when(rawLedger.rawPayloadsForFetcherWithFetchedAt("CTB", "ctb-stocktakes"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayloadWithRefAndTime(
                    UUID.randomUUID(),
                    bytes(HEADER_05),
                    null,
                    Instant.parse("2026-10-12T00:00:00Z"))));
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-stocktake-detail"))
        .thenReturn(
            List.of(new RawLedgerQuery.RawPayloadWithRef(UUID.randomUUID(), bytes(DETAIL), null)));
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-wastage-detail")).thenReturn(List.of());

    CanonicalStocktakeIngest stocktakeIngest = mock(CanonicalStocktakeIngest.class);
    CanonicalStockCountIngest stockCountIngest = mock(CanonicalStockCountIngest.class);
    CanonicalWastageIngest wastageIngest = mock(CanonicalWastageIngest.class);

    runner(rawLedger, stocktakeIngest, stockCountIngest, wastageIngest).run(null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockCountInput>> countCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(stockCountIngest).recordAll(countCaptor.capture());
    assertThat(countCaptor.getValue()).isEmpty();
  }

  private static StockWastageCanonicalizationRunner runner(
      RawLedgerQuery rawLedger,
      CanonicalStocktakeIngest stocktakeIngest,
      CanonicalStockCountIngest stockCountIngest,
      CanonicalWastageIngest wastageIngest) {
    return new StockWastageCanonicalizationRunner(
        rawLedger,
        new CtbStocktakeParser(),
        new CtbWastageParser(),
        stocktakeIngest,
        stockCountIngest,
        wastageIngest);
  }
}
