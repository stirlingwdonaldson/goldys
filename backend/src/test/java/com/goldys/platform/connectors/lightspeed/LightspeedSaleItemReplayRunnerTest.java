package com.goldys.platform.connectors.lightspeed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.RawLedgerQuery.RawPayload;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LightspeedSaleItemReplayRunnerTest {

  @Test
  void replaysEachStoredPayloadOneAtATime() {
    RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
    LightspeedSaleItemIngestService saleItems = mock(LightspeedSaleItemIngestService.class);
    UUID idA = UUID.randomUUID();
    UUID idB = UUID.randomUUID();
    byte[] a = {1};
    byte[] b = {2};
    when(rawLedger.rawRecordIdsForFetcher("lightspeed-sale-items")).thenReturn(List.of(idA, idB));
    when(rawLedger.payloadBytes(idA)).thenReturn(a);
    when(rawLedger.payloadBytes(idB)).thenReturn(b);

    new LightspeedSaleItemReplayRunner(rawLedger, saleItems).run(null);

    verify(saleItems).backfill(new RawPayload(idA, a));
    verify(saleItems).backfill(new RawPayload(idB, b));
  }

  @Test
  void skipsARecordWhoseBytesAreMissing() {
    RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
    LightspeedSaleItemIngestService saleItems = mock(LightspeedSaleItemIngestService.class);
    UUID id = UUID.randomUUID();
    when(rawLedger.rawRecordIdsForFetcher("lightspeed-sale-items")).thenReturn(List.of(id));
    when(rawLedger.payloadBytes(id)).thenReturn(null);

    new LightspeedSaleItemReplayRunner(rawLedger, saleItems).run(null);

    verify(saleItems, never()).backfill(any());
  }
}
