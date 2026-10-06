package com.goldys.platform.connectors.ctb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbConnectorTest {

  @Test
  void saleItemsBackfillsThe90DayWindow() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchRevenues(anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[]}", 0));
    when(client.searchSaleItems(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[]}", 0));

    CtbRevenueParser revenueParser = mock(CtbRevenueParser.class);
    when(revenueParser.parse(any(byte[].class))).thenReturn(List.of());
    CtbSaleItemParser saleItemParser = mock(CtbSaleItemParser.class);
    when(saleItemParser.parse(any(byte[].class))).thenReturn(List.of());

    CanonicalDailySalesIngest dailySales = mock(CanonicalDailySalesIngest.class);
    CanonicalProductSalesIngest productSales = mock(CanonicalProductSalesIngest.class);

    CtbConnector connector =
        new CtbConnector(client, "e", "p", revenueParser, dailySales, saleItemParser, productSales);

    connector.fetch(null, payload -> UUID.randomUUID());

    // The old behaviour searched a single day (one call); the backfill iterates the 90-day window.
    verify(client, atLeast(2)).searchSaleItems(anyString(), anyString(), anyInt(), anyInt());
  }
}
