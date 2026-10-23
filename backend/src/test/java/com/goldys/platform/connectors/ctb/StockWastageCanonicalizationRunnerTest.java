package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.goldys.platform.canonical.*;
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
      "{\"data\":[{\"stocktakeId\":1,\"stocktakeDate\":\"2026-10-05\"}]}";
  private static final String HEADER_06 =
      "{\"data\":[{\"stocktakeId\":1,\"stocktakeDate\":\"2026-10-06\"}]}";
  private static final String DETAIL =
      "{\"data\":[{\"id\":100,\"stockDesc\":\"Oil\",\"qtyCount\":5}]}";
  private static final String WASTE =
      "{\"data\":[{\"wastageRecordId\":7,\"wastageRecordDetailId\":359,\"recordDate\":\"2026-10-05\",\"stockDesc\":\"Oil\",\"quantity\":3}]}";
  final RawLedgerQuery ledger = mock(RawLedgerQuery.class);
  final CanonicalStocktakeIngest headers = mock(CanonicalStocktakeIngest.class);
  final CanonicalStockCountIngest counts = mock(CanonicalStockCountIngest.class);
  final CanonicalWastageIngest waste = mock(CanonicalWastageIngest.class);

  @Test
  void streamsPayloadBatchesAndRetainsTheirDateAndReceiptIdentity() {
    UUID old = given("ctb-stocktakes", HEADER_05, null, 1).id();
    var updated = ref(HEADER_06, null, 2);
    when(ledger.rawRecordRefsForFetcher("CTB", "ctb-stocktakes"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawRecordRef(old, null, Instant.EPOCH.plusSeconds(1)), updated));
    var a = ref(DETAIL, "stocktake:1", 3);
    var b = ref(DETAIL, "stocktake:1", 4);
    when(ledger.rawRecordRefsForFetcher("CTB", "ctb-stocktake-detail")).thenReturn(List.of(a, b));
    given("ctb-wastage-detail", WASTE, "wastage:7", 5);
    runner().run(null);
    var header = ArgumentCaptor.forClass(StocktakeInput.class);
    verify(headers).record(header.capture());
    assertThat(header.getValue().stocktakeDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(header.getValue().rawRecordId()).isEqualTo(updated.id());
    @SuppressWarnings("unchecked")
    var batches = ArgumentCaptor.forClass((Class<List<StockCountInput>>) (Class<?>) List.class);
    verify(counts, times(2)).recordAll(batches.capture());
    assertThat(batches.getAllValues())
        .allSatisfy(
            batch -> {
              assertThat(batch).hasSize(1);
              assertThat(batch.getFirst().countedDate()).isEqualTo(LocalDate.of(2026, 10, 6));
            });
    assertThat(batches.getAllValues().stream().map(batch -> batch.getFirst().rawRecordId()))
        .containsExactly(a.id(), b.id());
    verify(waste).recordAll(any());
  }

  @Test
  void missingHeaderFailsBeforeAnyCanonicalWrite() {
    given("ctb-stocktakes", HEADER_05, null, 1);
    given("ctb-stocktake-detail", DETAIL, "stocktake:999", 2);
    assertThatThrownBy(() -> runner().run(null)).isInstanceOf(ConnectorFetchException.class);
    verifyNoInteractions(headers, counts, waste);
  }

  @Test
  void unparseableDetailReferenceIsNotGivenAnInventedDate() {
    given("ctb-stocktakes", HEADER_05, null, 1);
    given("ctb-stocktake-detail", DETAIL, null, 2);
    runner().run(null);
    verify(headers).record(any());
    verifyNoInteractions(counts);
  }

  @Test
  void missingStoredBytesFailExplicitly() {
    var raw = new RawLedgerQuery.RawRecordRef(UUID.randomUUID(), null, Instant.EPOCH);
    when(ledger.rawRecordRefsForFetcher("CTB", "ctb-stocktakes")).thenReturn(List.of(raw));
    assertThatThrownBy(() -> runner().run(null))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("unavailable");
    verifyNoInteractions(headers, counts, waste);
  }

  private RawLedgerQuery.RawRecordRef given(
      String fetcher, String json, String sourceRef, int sequence) {
    var ref = ref(json, sourceRef, sequence);
    when(ledger.rawRecordRefsForFetcher("CTB", fetcher)).thenReturn(List.of(ref));
    return ref;
  }

  private RawLedgerQuery.RawRecordRef ref(String json, String sourceRef, int sequence) {
    UUID id = UUID.randomUUID();
    when(ledger.payloadBytes(id)).thenReturn(json.getBytes(StandardCharsets.UTF_8));
    return new RawLedgerQuery.RawRecordRef(id, sourceRef, Instant.EPOCH.plusSeconds(sequence));
  }

  private StockWastageCanonicalizationRunner runner() {
    return new StockWastageCanonicalizationRunner(
        ledger, new CtbStocktakeParser(), new CtbWastageParser(), headers, counts, waste);
  }
}
