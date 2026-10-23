package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.canonical.CanonicalWastageIngest;
import com.goldys.platform.canonical.ProductMappingIngest;
import com.goldys.platform.canonical.SaleRecipeLinkIngest;
import com.goldys.platform.canonical.SaleRecipeLinkInput;
import com.goldys.platform.canonical.SourceProductSaleFactIngest;
import com.goldys.platform.canonical.WastageInput;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import java.nio.file.Files;
import java.nio.file.Path;
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
        new CtbWastageParser(),
        mock(CanonicalWastageIngest.class),
        VenueClock.system(ZoneId.of("Australia/Melbourne")));
  }

  private static CtbConnector wastageConnector(CtbClient client, CanonicalWastageIngest ingest) {
    return new CtbConnector(
        client,
        "e",
        "p",
        emptyRevenueParser(),
        mock(CanonicalDailySalesIngest.class),
        emptySaleItemParser(),
        mock(CanonicalProductSalesIngest.class),
        mock(CtbSaleRecipeLinkParser.class),
        mock(SaleRecipeLinkIngest.class),
        mock(ProductMappingIngest.class),
        mock(SourceProductSaleFactIngest.class),
        new CtbWastageParser(),
        ingest,
        VenueClock.system(ZoneId.of("Australia/Melbourne")));
  }

  @Test
  void recentWastageDetailIsPulledRawFirstAndCanonicalized() throws Exception {
    CtbClient client = emptyClient();
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(
            new CtbClient.CtbPage(
                Files.readString(
                    Path.of("src/test/resources/fixtures/ctb/wastage_records_sample.json")),
                3));
    String detail =
        Files.readString(Path.of("src/test/resources/fixtures/ctb/wastage_detail_sample.json"));
    when(client.getWastageRecordDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage(detail, 3));
    CanonicalWastageIngest ingest = mock(CanonicalWastageIngest.class);
    List<FetchedPayload> written = new ArrayList<>();
    List<UUID> rawIds = new ArrayList<>();

    wastageConnector(client, ingest)
        .fetch(
            null,
            payload -> {
              written.add(payload);
              UUID id = UUID.randomUUID();
              rawIds.add(id);
              return id;
            });

    // Every header id in the sample (151, 150, 149) gets its lines fetched, newest first.
    verify(client).getWastageRecordDetail(151, 0, 200);
    verify(client).getWastageRecordDetail(150, 0, 200);
    verify(client).getWastageRecordDetail(149, 0, 200);
    assertThat(written)
        .filteredOn(p -> p.fetcherIdentity().equals("ctb-wastage-detail"))
        .extracting(FetchedPayload::sourceRecordRef)
        .containsExactly("wastage:151", "wastage:150", "wastage:149");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<WastageInput>> captor = ArgumentCaptor.forClass((Class) List.class);
    verify(ingest, times(3)).recordAll(captor.capture());
    // Canonical facts carry the id of the raw detail page they were parsed from.
    List<UUID> detailRawIds = new ArrayList<>();
    for (int i = 0; i < written.size(); i++) {
      if (written.get(i).fetcherIdentity().equals("ctb-wastage-detail")) {
        detailRawIds.add(rawIds.get(i));
      }
    }
    for (int i = 0; i < 3; i++) {
      UUID expectedRaw = detailRawIds.get(i);
      assertThat(captor.getAllValues().get(i))
          .hasSize(3)
          .allSatisfy(w -> assertThat(w.rawRecordId()).isEqualTo(expectedRaw));
    }
  }

  @Test
  void onlyTheNewestWastageRecordsAreDetailed() {
    CtbClient client = emptyClient();
    StringBuilder headers = new StringBuilder("{\"data\":[");
    int count = CtbConnector.RECENT_WASTAGE_RECORDS + 5;
    for (int id = 1; id <= count; id++) {
      headers.append(id > 1 ? "," : "").append("{\"wastageRecordId\":").append(id).append('}');
    }
    headers.append("]}");
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage(headers.toString(), count));
    when(client.getWastageRecordDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[]}", 0));

    wastageConnector(client, mock(CanonicalWastageIngest.class))
        .fetch(null, payload -> UUID.randomUUID());

    verify(client, times(CtbConnector.RECENT_WASTAGE_RECORDS))
        .getWastageRecordDetail(anyInt(), anyInt(), anyInt());
    verify(client).getWastageRecordDetail(count, 0, 200);
    verify(client, never()).getWastageRecordDetail(eq(5), anyInt(), anyInt());
  }

  @Test
  void wastageDetailPagesUntilTotalCount() {
    CtbClient client = emptyClient();
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"wastageRecordId\":9}]}", 1));
    when(client.getWastageRecordDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[]}", 450));

    wastageConnector(client, mock(CanonicalWastageIngest.class))
        .fetch(null, payload -> UUID.randomUUID());

    verify(client).getWastageRecordDetail(9, 0, 200);
    verify(client).getWastageRecordDetail(9, 200, 200);
    verify(client).getWastageRecordDetail(9, 400, 200);
    verify(client, times(3)).getWastageRecordDetail(eq(9), anyInt(), anyInt());
  }

  @Test
  void aBadWastageRecordFailsAloneAndTheRunIsPartial() throws Exception {
    CtbClient client = emptyClient();
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(
            new CtbClient.CtbPage(
                "{\"data\":[{\"wastageRecordId\":2},{\"wastageRecordId\":1}]}", 2));
    String good =
        Files.readString(Path.of("src/test/resources/fixtures/ctb/wastage_detail_sample.json"));
    when(client.getWastageRecordDetail(eq(2), anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("not json", 1));
    when(client.getWastageRecordDetail(eq(1), anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage(good, 3));
    CanonicalWastageIngest ingest = mock(CanonicalWastageIngest.class);
    List<String> refs = new ArrayList<>();

    assertThatThrownBy(
            () ->
                wastageConnector(client, ingest)
                    .fetch(
                        null,
                        payload -> {
                          refs.add(payload.sourceRecordRef());
                          return UUID.randomUUID();
                        }))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");

    // The bad payload is still kept raw (evidence), and the good record is still canonicalized.
    assertThat(refs).contains("wastage:2", "wastage:1");
    verify(ingest, times(1)).recordAll(any());
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
    when(client.getWastageRecordDetail(anyInt(), anyInt(), anyInt())).thenReturn(empty);
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
