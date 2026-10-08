package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.RawFilter;
import com.goldys.platform.semantic.RawRecordDetail;
import com.goldys.platform.semantic.RawRecordSummary;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class RawRecordBrowseQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired RawRecordBrowseQuery browse;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table raw_record, ingestion_failure, ingestion_run cascade");
  }

  @Test
  void listFiltersBySourceAndPaginatesRecentFirst() {
    insert("CTB", "ctb-invoices-ajax", "API", Instant.parse("2026-10-01T00:00:00Z"));
    insert("CTB", "ctb-recipes", "API", Instant.parse("2026-10-02T00:00:00Z"));
    insert(
        "LIGHTSPEED", "lightspeed-insights", "FILE_EXPORT", Instant.parse("2026-10-03T00:00:00Z"));

    DataPage<RawRecordSummary> page =
        browse.list(new RawFilter("CTB", null, null, null, null), 0, 2);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).hasSize(2);
    assertThat(page.items()).allMatch(r -> r.sourceSystem().equals("CTB"));
    assertThat(page.items().get(0).fetcherIdentity()).isEqualTo("ctb-recipes");
  }

  @Test
  void listFiltersByFetcherAndMethod() {
    insert("CTB", "ctb-invoices-ajax", "API", Instant.parse("2026-10-01T00:00:00Z"));
    insert("CTB", "ctb-recipes", "API", Instant.parse("2026-10-02T00:00:00Z"));

    DataPage<RawRecordSummary> page =
        browse.list(new RawFilter(null, "ctb-invoices-ajax", "API", null, null), 0, 10);

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().get(0).fetcherIdentity()).isEqualTo("ctb-invoices-ajax");
  }

  @Test
  void listReturnsMetadataWithoutPayloadBytes() {
    UUID id =
        insert("CTB", "ctb-recipes", "API", Instant.parse("2026-10-01T00:00:00Z"), "{\"a\":1}");

    RawRecordSummary row =
        browse.list(new RawFilter(null, null, null, null, null), 0, 10).items().get(0);

    assertThat(row.id()).isEqualTo(id);
    assertThat(row.byteLength()).isEqualTo(7L);
    assertThat(row.contentType()).isEqualTo("application/json");
    assertThat(row.sha256()).isEqualTo("0".repeat(64));
  }

  @Test
  void byIdReturnsPayloadAndJsonFlag() {
    UUID id =
        insert("CTB", "ctb-recipes", "API", Instant.parse("2026-10-01T00:00:00Z"), "{\"a\":1}");

    RawRecordDetail detail = browse.byId(id);

    assertThat(detail.summary().id()).isEqualTo(id);
    assertThat(detail.payload()).isEqualTo("{\"a\":1}");
    assertThat(detail.isJson()).isTrue();
  }

  @Test
  void byIdMarksNonJsonPayloads() {
    UUID id =
        insert("CTB", "ctb-recipes", "API", Instant.parse("2026-10-01T00:00:00Z"), "not json");

    RawRecordDetail detail = browse.byId(id);

    assertThat(detail.isJson()).isFalse();
  }

  private UUID insert(String source, String fetcher, String method, Instant at) {
    return insert(source, fetcher, method, at, "{}");
  }

  private UUID insert(String source, String fetcher, String method, Instant at, String payload) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId,
        source);
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, ?, 'application/json', ?, ?, ?, ?, ?)",
        recordId,
        runId,
        source,
        method,
        bytes,
        "0".repeat(64),
        bytes.length,
        fetcher,
        java.sql.Timestamp.from(at));
    return recordId;
  }
}
