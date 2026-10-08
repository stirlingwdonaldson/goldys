package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbConnectorTest {

  @Test
  void saleItemsBackfillsThe90DayWindow() {
    CtbClient client = emptyClient();
    CtbRevenueParser revenueParser = emptyRevenueParser();
    CtbSaleItemParser saleItemParser = emptySaleItemParser();

    CtbConnector connector = connector(client, revenueParser, saleItemParser);

    connector.fetch(null, payload -> UUID.randomUUID());

    // The old behaviour searched a single day (one call); the backfill iterates the 90-day window.
    verify(client, atLeast(2)).searchSaleItems(anyString(), anyString(), anyInt(), anyInt());
  }

  @Test
  void inventoryEndpointsArePulledToRawLedgerWithTheirFetcherIdentity() {
    CtbClient client = emptyClient();
    CtbConnector connector = connector(client, emptyRevenueParser(), emptySaleItemParser());

    List<String> identities = new ArrayList<>();
    connector.fetch(
        null,
        payload -> {
          identities.add(payload.fetcherIdentity());
          return UUID.randomUUID();
        });

    assertThat(identities)
        .contains(
            "ctb-invoices-ajax",
            "ctb-sale-recipe-links",
            "ctb-recipes",
            "ctb-stocks",
            "ctb-suppliers",
            "ctb-stocktakes",
            "ctb-wastage",
            "ctb-stock-orders",
            "ctb-statements",
            "ctb-variance",
            "ctb-missing-revenue",
            "ctb-reference-data");
    verify(client).getAllRecipes();
    verify(client).getAllSuppliers();
    verify(client).searchInvoices(anyInt(), anyInt());
  }

  @Test
  void permissionDeniedFromClientPropagates() {
    CtbClient client = emptyClient();
    when(client.searchInvoices(anyInt(), anyInt()))
        .thenThrow(
            new ConnectorFetchException(
                "CONNECTOR_PERMISSION_DENIED", "CTB permission denied: Invoice/SearchInvoices"));

    CtbConnector connector = connector(client, emptyRevenueParser(), emptySaleItemParser());

    assertThatThrownBy(() -> connector.fetch(null, payload -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_PERMISSION_DENIED");
  }

  private static CtbConnector connector(
      CtbClient client, CtbRevenueParser revenueParser, CtbSaleItemParser saleItemParser) {
    return new CtbConnector(
        client,
        "e",
        "p",
        revenueParser,
        mock(CanonicalDailySalesIngest.class),
        saleItemParser,
        mock(CanonicalProductSalesIngest.class));
  }

  private static CtbRevenueParser emptyRevenueParser() {
    CtbRevenueParser parser = mock(CtbRevenueParser.class);
    when(parser.parse(any(byte[].class))).thenReturn(List.of());
    return parser;
  }

  private static CtbSaleItemParser emptySaleItemParser() {
    CtbSaleItemParser parser = mock(CtbSaleItemParser.class);
    when(parser.parse(any(byte[].class))).thenReturn(List.of());
    return parser;
  }

  private static CtbClient emptyClient() {
    CtbClient client = mock(CtbClient.class);
    CtbClient.CtbPage empty = new CtbClient.CtbPage("{\"data\":[]}", 0);
    when(client.searchRevenues(anyInt(), anyInt())).thenReturn(empty);
    when(client.searchSaleItems(anyString(), anyString(), anyInt(), anyInt())).thenReturn(empty);
    when(client.searchInvoices(anyInt(), anyInt())).thenReturn(empty);
    when(client.searchDistinctSaleItemsForLinking(anyInt(), anyInt())).thenReturn(empty);
    when(client.getAllRecipes()).thenReturn(empty);
    when(client.searchStocks(anyInt(), anyInt())).thenReturn(empty);
    when(client.getAllSuppliers()).thenReturn(empty);
    when(client.searchStocktakes(anyInt(), anyInt())).thenReturn(empty);
    when(client.searchWastageRecords(anyInt(), anyInt())).thenReturn(empty);
    when(client.searchStockOrders(anyInt(), anyInt())).thenReturn(empty);
    when(client.searchStatements(anyInt(), anyInt(), anyString(), anyString())).thenReturn(empty);
    when(client.getVarianceReportData(anyString(), anyString())).thenReturn(empty);
    when(client.missingRevenueReport()).thenReturn(empty);
    when(client.getAllDepartments()).thenReturn(empty);
    when(client.getAllActivities()).thenReturn(empty);
    when(client.getAllStockCategories()).thenReturn(empty);
    when(client.getAllUoms()).thenReturn(empty);
    when(client.getAllSupplierMeasurements()).thenReturn(empty);
    when(client.getAllMeasurementConversions()).thenReturn(empty);
    return client;
  }
}
