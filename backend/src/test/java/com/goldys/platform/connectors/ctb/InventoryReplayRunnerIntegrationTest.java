package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalStockCountIngest;
import com.goldys.platform.canonical.CanonicalStocktakeIngest;
import com.goldys.platform.canonical.CanonicalWastageIngest;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.semantic.InventoryObservationQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Separate test context: this full-ledger replay must not read other suites' raw fixtures. */
@SpringBootTest(properties = "test.inventory-replay-runner=true")
@Import(PostgresContainerConfiguration.class)
class InventoryReplayRunnerIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired RawLedgerQuery ledger;
  @Autowired CanonicalStocktakeIngest headers;
  @Autowired CanonicalStockCountIngest counts;
  @Autowired CanonicalWastageIngest waste;
  @Autowired InventoryObservationQuery observations;

  @Test
  void repeatFullPassesRetainVersionsAndApplyHeaderDateCorrections() throws Exception {
    raw(1, "ctb-stocktakes", null, header("2026-10-05"));
    raw(2, "ctb-stocktake-detail", "stocktake:1", detail("5"));
    raw(3, "ctb-stocktake-detail", "stocktake:1", detail("6"));
    raw(4, "ctb-wastage-detail", "wastage:7", waste("3"));
    raw(5, "ctb-wastage-detail", "wastage:7", waste("4"));
    var runner =
        new StockWastageCanonicalizationRunner(
            ledger, new CtbStocktakeParser(), new CtbWastageParser(), headers, counts, waste);
    runner.run(null);
    runner.run(null);
    assertThat(versions("canonical_stock_count")).isEqualTo(2);
    assertThat(versions("canonical_wastage")).isEqualTo(2);
    assertThat(versions("inventory_observation_receipt")).isEqualTo(5);
    raw(6, "ctb-stocktakes", null, header("2026-10-06"));
    runner.run(null);
    runner.run(null);
    assertThat(versions("canonical_stock_count")).isEqualTo(3);
    assertThat(versions("canonical_wastage")).isEqualTo(2);
    assertThat(versions("canonical_stocktake")).isEqualTo(2);
    assertThat(versions("inventory_observation_receipt")).isEqualTo(8);
    assertThat(
            observations.observations(
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-05"), "COUNT", 0, 50))
        .isEmpty();
    assertThat(
            observations.observations(
                LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-06"), "COUNT", 0, 50))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.quantity()).isEqualByComparingTo("6");
              assertThat(row.unit()).isEqualTo("KG");
            });
    assertThat(
            observations.observations(
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06"), "WASTE", 0, 50))
        .singleElement()
        .satisfies(row -> assertThat(row.quantity()).isEqualByComparingTo("4"));
  }

  private int versions(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Integer.class);
  }

  private String header(String date) {
    return "{\"data\":[{\"stocktakeId\":1,\"stocktakeDate\":\""
        + date
        + "\",\"isPartial\":true,\"isLockStocktake\":false}]}";
  }

  private String detail(String qty) {
    return "{\"data\":[{\"id\":100,\"stockDesc\":\"Oil\",\"qtyCount\":"
        + qty
        + ",\"stockUnitOfMeasurementName\":\"KG\"}]}";
  }

  private String waste(String qty) {
    return "{\"data\":[{\"wastageRecordId\":7,\"wastageRecordDetailId\":359,\"recordDate\":\"2026-10-05\",\"stockDesc\":\"Oil\",\"quantity\":"
        + qty
        + ",\"unitOfMeasurementName\":\"KG\"}]}";
  }

  private void raw(int seq, String fetcher, String ref, String json) throws Exception {
    UUID run = UUID.randomUUID(), raw = UUID.randomUUID();
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    jdbc.update(
        "insert into ingestion_run(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) values (?,'CTB','fixture','SUCCESS',now(),1,1)",
        run);
    jdbc.update(
        "insert into raw_record(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,payload_sha256,payload_byte_length,fetcher_identity,source_record_ref,fetched_at) values (?,?,'CTB','API','application/json',?,?,?,?,?,?)",
        raw,
        run,
        bytes,
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
        bytes.length,
        fetcher,
        ref,
        Timestamp.from(Instant.parse("2026-10-12T00:00:00Z").plusSeconds(seq)));
  }
}
