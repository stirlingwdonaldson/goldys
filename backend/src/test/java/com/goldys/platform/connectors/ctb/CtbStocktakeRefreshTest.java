package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.canonical.*;
import com.goldys.platform.ingestion.port.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class CtbStocktakeRefreshTest {
  private final CtbClient client = mock(CtbClient.class);
  private final CanonicalStocktakeIngest headers = mock(CanonicalStocktakeIngest.class);
  private final CanonicalStockCountIngest counts = mock(CanonicalStockCountIngest.class);
  private final CtbStocktakeRefresh refresh =
      new CtbStocktakeRefresh(new CtbStocktakeParser(), headers, counts);

  @Test
  void realHeaderAndLinesAreStoredRawBeforeCanonicalization() throws Exception {
    when(client.searchStocktakes(0, 200))
        .thenReturn(new CtbClient.CtbPage(fixture("weekly-stocktake-header.json"), 1));
    when(client.getStocktakeDetail(38, 0, 200))
        .thenReturn(new CtbClient.CtbPage(fixture("weekly-stocktake-lines.json"), 3));
    IngestionSink sink = mock(IngestionSink.class);
    when(sink.accept(any())).thenAnswer(i -> UUID.randomUUID());
    refresh.fetch(client, sink);
    var ordered = inOrder(sink, headers, counts);
    ordered.verify(sink).accept(argThat(p -> p.fetcherIdentity().equals("ctb-stocktakes")));
    ordered
        .verify(headers)
        .record(argThat(h -> h.stocktakeDate().equals(LocalDate.parse("2026-10-12"))));
    ordered.verify(sink).accept(argThat(p -> "stocktake:38".equals(p.sourceRecordRef())));
    ordered
        .verify(counts)
        .recordAll(
            argThat(
                lines ->
                    lines.size() == 3
                        && lines.stream().anyMatch(l -> l.quantityOnHand().signum() > 0)));
  }

  @Test
  void truncatedDetailIsRetainedRawButNeverPublishedAsComplete() throws Exception {
    when(client.searchStocktakes(0, 200))
        .thenReturn(new CtbClient.CtbPage(fixture("weekly-stocktake-header.json"), 1));
    when(client.getStocktakeDetail(38, 0, 200))
        .thenReturn(new CtbClient.CtbPage(fixture("weekly-stocktake-lines.json"), 4));
    IngestionSink sink = p -> UUID.randomUUID();
    assertThatThrownBy(() -> refresh.fetch(client, sink))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("stocktake:38");
    verifyNoInteractions(counts);
  }

  @Test
  void repeatedSourceRowsCannotStandInForACompleteUniqueStocktake() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var sample = mapper.readTree(fixture("weekly-stocktake-lines.json")).path("data").get(0);
    var first = mapper.createArrayNode();
    for (int i = 1; i <= 200; i++)
      first.add(((com.fasterxml.jackson.databind.node.ObjectNode) sample.deepCopy()).put("id", i));
    var last = mapper.createArrayNode();
    for (int i = 0; i < 9; i++) last.add(first.get(i).deepCopy());
    when(client.searchStocktakes(0, 200))
        .thenReturn(new CtbClient.CtbPage(fixture("weekly-stocktake-header.json"), 1));
    when(client.getStocktakeDetail(38, 0, 200))
        .thenReturn(
            new CtbClient.CtbPage(mapper.createObjectNode().set("data", first).toString(), 209));
    when(client.getStocktakeDetail(38, 200, 200))
        .thenReturn(
            new CtbClient.CtbPage(mapper.createObjectNode().set("data", last).toString(), 209));
    IngestionSink sink = mock(IngestionSink.class);
    when(sink.accept(any())).thenAnswer(i -> UUID.randomUUID());
    assertThatThrownBy(() -> refresh.fetch(client, sink))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("unique source population is unverified");
    verify(sink, times(3)).accept(any());
    verifyNoInteractions(counts);
  }

  private String fixture(String name) throws Exception {
    try (var input = getClass().getResourceAsStream("/ctb/" + name)) {
      return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void permissionFailureKeepsItsClassification() throws Exception {
    when(client.searchStocktakes(0, 200))
        .thenReturn(new CtbClient.CtbPage(fixture("weekly-stocktake-header.json"), 1));
    when(client.getStocktakeDetail(38, 0, 200))
        .thenThrow(
            new ConnectorFetchException("CONNECTOR_PERMISSION_DENIED", "Stocktake detail denied"));
    assertThatThrownBy(() -> refresh.fetch(client, p -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_PERMISSION_DENIED");
    verifyNoInteractions(counts);
  }

  @Test
  void invalidEnglishDatesAreRejectedRatherThanAdjusted() {
    var json = "{\"data\":[{\"stocktakeId\":38,\"stocktakeDate\":\"Thursday, 31 April 2026\"}]}";
    assertThatThrownBy(
            () ->
                new CtbStocktakeParser()
                    .parseStocktakes(json.getBytes(StandardCharsets.UTF_8), UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class);
  }
}
