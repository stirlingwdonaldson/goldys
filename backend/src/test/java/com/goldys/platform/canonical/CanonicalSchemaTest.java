package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalSchemaTest {
  private static final String INSERT_SALE =
      "insert into canonical_sale_item "
          + "(id, logical_entity_id, source_system, source_record_ref, raw_record_id, "
          + "valid_from, recorded_at, item_name, quantity_sold, amount) "
          + "values (?, ?, ?, ?, ?, now(), now(), 'Burger', 1, 18.00)";

  @Autowired JdbcTemplate jdbc;

  @Test
  void sameSourceFactCannotHaveTwoCurrentVersions() {
    UUID rawRecordId = seedRawRecord();

    jdbc.update(
        INSERT_SALE,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "LIGHTSPEED",
        "sale-line-1",
        rawRecordId);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_SALE,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "LIGHTSPEED",
                    "sale-line-1",
                    rawRecordId))
        .hasRootCauseInstanceOf(SQLException.class);
  }

  @Test
  void canonicalRowRequiresRawProvenance() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_SALE,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "LIGHTSPEED",
                    "sale-line-2",
                    null))
        .hasRootCauseInstanceOf(SQLException.class);
  }

  private UUID seedRawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    byte[] payload = {(byte) 0x50, (byte) 0x4b};

    jdbc.update(
        "insert into ingestion_run "
            + "(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) "
            + "values (?, 'LIGHTSPEED', 'fixture', 'RUNNING', now(), 0, 0)",
        runId);
    jdbc.update(
        "insert into raw_record "
            + "(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,"
            + "payload_sha256,payload_byte_length,fetcher_identity,fetched_at) "
            + "values (?, ?, 'LIGHTSPEED', 'API', 'application/json', ?, ?, ?, 'fixture', now())",
        recordId,
        runId,
        payload,
        "0".repeat(64),
        payload.length);
    return recordId;
  }
}
