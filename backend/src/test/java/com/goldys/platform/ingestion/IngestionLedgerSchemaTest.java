package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class IngestionLedgerSchemaTest {
  @Autowired JdbcTemplate jdbc;

  @Test
  void rawRecordPreservesBytesAndRejectsMutation() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    byte[] payload = {(byte) 0x50, (byte) 0x4b, (byte) 0x03, (byte) 0x04, (byte) 0xff};

    jdbc.update(
        "insert into ingestion_run "
            + "(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) "
            + "values (?, 'CTB', 'fixture', 'RUNNING', now(), 0, 0)",
        runId);
    jdbc.update(
        "insert into raw_record "
            + "(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,"
            + "payload_sha256,payload_byte_length,fetcher_identity,fetched_at) "
            + "values (?, ?, 'CTB', 'FILE_EXPORT', 'application/octet-stream', "
            + "?, ?, ?, 'fixture', now())",
        recordId,
        runId,
        payload,
        "0".repeat(64),
        payload.length);

    assertThat(
            jdbc.queryForObject(
                "select payload_bytes from raw_record where id = ?", byte[].class, recordId))
        .containsExactly(payload);
    assertThatThrownBy(
            () ->
                jdbc.update("update raw_record set content_type='text/plain' where id=?", recordId))
        .rootCause()
        .hasMessageContaining("raw_record is append-only");
    assertThatThrownBy(() -> jdbc.update("delete from raw_record where id=?", recordId))
        .rootCause()
        .hasMessageContaining("raw_record is append-only");
  }
}
