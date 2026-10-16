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
import com.goldys.platform.canonical.ProductMappingIngest;
import com.goldys.platform.canonical.SaleRecipeLinkIngest;
import com.goldys.platform.canonical.SaleRecipeLinkInput;
import com.goldys.platform.canonical.SourceProductSaleFactIngest;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

  @Test
  void aFailingDatasetDoesNotAbortTheRest() {
    // The production failure: recipes returns a non-JSON (HTML) body. Before isolation this aborted
    // the whole fetch, so stocks/suppliers/stocktakes/wastage/orders/statements/reference never
    // ran.
    CtbClient client = emptyClient();
    when(client.getAllRecipes())
        .thenThrow(
            new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response"));

    CtbConnector connector = connector(client, emptyRevenueParser(), emptySaleItemParser());
    List<String> identities = new ArrayList<>();

    assertThatThrownBy(
            () ->
                connector.fetch(
                    null,
                    payload -> {
                      identities.add(payload.fetcherIdentity());
                      return UUID.randomUUID();
                    }))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");

    // Datasets after the failing recipes pull are still attempted and written.
    assertThat(identities)
        .contains(
            "ctb-stocks",
            "ctb-suppliers",
            "ctb-stocktakes",
            "ctb-wastage",
            "ctb-stock-orders",
            "ctb-statements",
            "ctb-reference-data");
  }

  @Test
  void multipleDatasetFailuresAreSummarised() {
    CtbClient client = emptyClient();
    when(client.getAllRecipes())
        .thenThrow(
            new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response"));
    when(client.searchStocks(anyInt(), anyInt()))
        .thenThrow(new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "CTB Stock/SearchStocks"));

    CtbConnector connector = connector(client, emptyRevenueParser(), emptySaleItemParser());

    assertThatThrownBy(() -> connector.fetch(null, payload -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("ctb-recipes")
        .hasMessageContaining("ctb-stocks");
  }

  @Test
  void pageCapTruncationIsReportedRatherThanSilent() {
    // totalCount far beyond MAX_PAGES * PAGE_SIZE, so the paging loop hits the cap with more to
    // pull.
    CtbClient client = emptyClient();
    when(client.searchInvoices(anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[]}", 60000));

    CtbConnector connector = connector(client, emptyRevenueParser(), emptySaleItemParser());

    assertThatThrownBy(() -> connector.fetch(null, payload -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_TRUNCATED");
  }

  private static CtbConnector connector(
      CtbClient client, CtbRevenueParser revenueParser, CtbSaleItemParser saleItemParser) {
    return connector(
        client,
        revenueParser,
        saleItemParser,
        mock(CtbSaleRecipeLinkParser.class),
        mock(SaleRecipeLinkIngest.class));
  }

  private static CtbConnector connector(
      CtbClient client,
      CtbRevenueParser revenueParser,
      CtbSaleItemParser saleItemParser,
      CtbSaleRecipeLinkParser saleRecipeLinkParser,
      SaleRecipeLinkIngest saleRecipeLinkIngest) {
    return new CtbConnector(
        client,
        "e",
        "p",
        revenueParser,
        mock(CanonicalDailySalesIngest.class),
        saleItemParser,
        mock(CanonicalProductSalesIngest.class),
        saleRecipeLinkParser,
        saleRecipeLinkIngest,
        mock(ProductMappingIngest.class),
        mock(SourceProductSaleFactIngest.class),
        VenueClock.system(ZoneId.of("Australia/Melbourne")));
  }

  @Test
  void saleRecipeLinksAreCanonicalized() {
    CtbClient client = emptyClient();
    String json =
        "{\"data\":[{\"saleItemRecipeId\":9001,\"saleItemStockCode\":\"STOCK-1\","
            + "\"saleItemStockDescription\":\"Kids Fish\",\"recipeId\":627,\"recipeName\":\"Kids Fish\","
            + "\"recipeFolderName\":\"Food\",\"isRecipeActive\":true},"
            + "{\"saleItemRecipeId\":9002,\"saleItemStockCode\":\"STOCK-2\","
            + "\"saleItemStockDescription\":\"Parma\",\"recipeId\":628,\"recipeName\":\"Parma\","
            + "\"recipeFolderName\":\"Mains\",\"isRecipeActive\":true}]}";
    when(client.searchDistinctSaleItemsForLinking(anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage(json, 2));

    SaleRecipeLinkIngest ingest = mock(SaleRecipeLinkIngest.class);
    CtbConnector connector =
        connector(
            client,
            emptyRevenueParser(),
            emptySaleItemParser(),
            new CtbSaleRecipeLinkParser(),
            ingest);

    connector.fetch(null, payload -> UUID.randomUUID());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SaleRecipeLinkInput>> captor = ArgumentCaptor.forClass((Class) List.class);
    verify(ingest).recordAll(captor.capture());
    assertThat(captor.getValue())
        .extracting(SaleRecipeLinkInput::recipeId)
        .containsExactly(627, 628);
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
    when(client.getStatements(anyString(), anyString())).thenReturn(empty);
    when(client.getAllDepartments()).thenReturn(empty);
    when(client.getAllActivities()).thenReturn(empty);
    when(client.getAllStockCategories()).thenReturn(empty);
    when(client.getAllUoms()).thenReturn(empty);
    when(client.getAllSupplierMeasurements()).thenReturn(empty);
    when(client.getAllMeasurementConversions()).thenReturn(empty);
    return client;
  }
}
