package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.goldys.platform.canonical.CanonicalStockCountIngest;
import com.goldys.platform.canonical.CanonicalStocktakeIngest;
import com.goldys.platform.connectors.ctb.CtbClient;
import com.goldys.platform.connectors.ctb.CtbStocktakeParser;
import com.goldys.platform.connectors.ctb.CtbStocktakeRefresh;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CtbStocktakeDuplicateIntegrationTest {
  @Autowired ConnectorRunner runner;
  @Autowired CanonicalStocktakeIngest headers;
  @Autowired CanonicalStockCountIngest counts;
  @Autowired JdbcTemplate jdbc;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void duplicateDeliveryRetainsBytesAndOnlyPublishesAnUnambiguousCount(boolean conflicting)
      throws Exception {
    int parent = conflicting ? 9502 : 9501;
    var client = mock(CtbClient.class);
    String header =
        "{\"data\":[{\"stocktakeId\":" + parent + ",\"stocktakeDate\":\"2026-11-09\"}]}";
    String row = "{\"id\":1,\"stockDesc\":\"Oil\",\"qtyCount\":5,\"subTotal\":12.50}";
    String first = "{\"data\":[" + String.join(",", Collections.nCopies(200, row)) + "]}";
    String last = "{\"data\":[" + (conflicting ? row.replace("12.50", "15.00") : row) + "]}";
    when(client.searchStocktakes(0, 200)).thenReturn(new CtbClient.CtbPage(header, 1));
    when(client.getStocktakeDetail(parent, 0, 200)).thenReturn(new CtbClient.CtbPage(first, 201));
    when(client.getStocktakeDetail(parent, 200, 200)).thenReturn(new CtbClient.CtbPage(last, 201));
    var refresh = new CtbStocktakeRefresh(new CtbStocktakeParser(), headers, counts);
    SourceConnector connector =
        new SourceConnector() {
          public String sourceSystem() {
            return "CTB";
          }

          public String connectorName() {
            return "stocktake-duplicate-test";
          }

          public void fetch(String watermark, IngestionSink sink) {
            refresh.fetch(client, sink);
          }
        };
    var completed = new CompletableFuture<IngestionStatus>();
    var run = runner.run(connector, null, completed::complete);
    assertThat(completed.get(30, TimeUnit.SECONDS))
        .isEqualTo(conflicting ? IngestionStatus.PARTIAL : IngestionStatus.SUCCESS);
    assertThat(
            jdbc.queryForList(
                "select payload_bytes from raw_record where ingestion_run_id=? and fetcher_identity='ctb-stocktake-detail' order by fetched_at",
                byte[].class,
                run))
        .containsExactly(
            first.getBytes(StandardCharsets.UTF_8), last.getBytes(StandardCharsets.UTF_8));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ingestion_failure where ingestion_run_id=?",
                Integer.class,
                run))
        .isEqualTo(conflicting ? 1 : 0);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_stock_count where stocktake_id=? and superseded_at is null",
                Integer.class,
                parent))
        .isEqualTo(conflicting ? 0 : 1);
    if (!conflicting) {
      assertThat(
              jdbc.queryForObject(
                  "select sum(source_value) from resolved_inventory_observation where source_system='CTB' and kind='COUNT' and parent_source_id=?",
                  BigDecimal.class,
                  parent))
          .isEqualByComparingTo("12.50");
    }
  }
}
