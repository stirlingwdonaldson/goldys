package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalVersionIntegrationTest {
  private static final Instant T1 = Instant.parse("2026-09-19T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-09-19T11:00:00Z");

  @Autowired CanonicalSaleItemService service;
  @Autowired CanonicalSaleItemRepository repository;
  @Autowired JdbcTemplate jdbc;

  private static SaleItemInput input(String ref, int qty, BigDecimal amount, UUID raw) {
    return new SaleItemInput("LIGHTSPEED", ref, "Burger", qty, amount, raw);
  }

  private static BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  @Test
  void unchangedRetryDoesNotCreateAnotherVersion() {
    UUID raw1 = seedRawRecord();
    UUID raw2 = seedRawRecord();

    var first = service.record(input("sale-retry", 1, money("18.00"), raw1));
    var retry = service.record(input("sale-retry", 1, money("18.00"), raw2));

    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(
            repository.findAllBySourceSystemAndSourceRecordRefOrderByRecordedAt(
                "LIGHTSPEED", "sale-retry"))
        .hasSize(1);
  }

  @Test
  void correctionPreservesSystemTimeHistory() {
    UUID raw1 = seedRawRecord();
    UUID raw2 = seedRawRecord();

    var first = service.recordAt(input("sale-correction", 1, money("18.00"), raw1), T1);
    var second = service.recordAt(input("sale-correction", 2, money("36.00"), raw2), T2);

    assertThat(repository.findKnownAt(first.logicalEntityId(), "LIGHTSPEED", T1.plusSeconds(1)))
        .get()
        .extracting(CanonicalSaleItem::quantitySold)
        .isEqualTo(1);
    assertThat(repository.findCurrent(first.logicalEntityId(), "LIGHTSPEED"))
        .get()
        .extracting(CanonicalSaleItem::quantitySold)
        .isEqualTo(2);
    assertThat(second.logicalEntityId()).isEqualTo(first.logicalEntityId());
  }

  @Test
  void newSourceFactGetsItsOwnLogicalIdentity() {
    UUID raw1 = seedRawRecord();

    var first = service.record(input("sale-new-a", 1, money("18.00"), raw1));
    var unrelated = service.record(input("sale-new-b", 1, money("18.00"), raw1));

    assertThat(unrelated.logicalEntityId()).isNotEqualTo(first.logicalEntityId());
  }

  @Test
  void concurrentCorrectionsLeaveExactlyOneCurrentVersion() throws Exception {
    UUID raw = seedRawRecord();
    service.record(input("sale-concurrent", 1, money("18.00"), raw));

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    int committed;
    try {
      Callable<Void> correctToTwo =
          () -> {
            barrier.await();
            service.record(input("sale-concurrent", 2, money("36.00"), raw));
            return null;
          };
      Callable<Void> correctToThree =
          () -> {
            barrier.await();
            service.record(input("sale-concurrent", 3, money("54.00"), raw));
            return null;
          };

      Future<Void> two = executor.submit(correctToTwo);
      Future<Void> three = executor.submit(correctToThree);

      committed = 0;
      for (Future<Void> future : List.of(two, three)) {
        try {
          future.get();
          committed++;
        } catch (ExecutionException e) {
          // The losing correction may receive a concurrency exception; the database invariant below
          // is what matters.
        }
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(committed).isGreaterThanOrEqualTo(1);
    Integer current =
        jdbc.queryForObject(
            "select count(*) from canonical_sale_item where source_system = 'LIGHTSPEED' "
                + "and source_record_ref = 'sale-concurrent' and superseded_at is null",
            Integer.class);
    assertThat(current).isEqualTo(1);
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
