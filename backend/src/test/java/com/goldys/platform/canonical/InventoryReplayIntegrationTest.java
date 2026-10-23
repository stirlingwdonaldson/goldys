package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.semantic.InventoryObservationQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class InventoryReplayIntegrationTest {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 5);
  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalStocktakeIngest headers;
  @Autowired CanonicalStockCountIngest counts;
  @Autowired CanonicalWastageIngest waste;
  @Autowired InventoryObservationQuery query;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate inventory_observation_receipt,canonical_stock_count,canonical_stocktake,canonical_wastage");
  }

  @Test
  void replayCannotRestorePreviousFactsOrManufactureHistory() {
    UUID first = raw(1), correction = raw(2);
    recordAllKinds(first, DATE, "5");
    recordAllKinds(correction, DATE.plusDays(1), "6");
    recordAllKinds(first, DATE, "5");
    assertCurrent("6", DATE.plusDays(1));
    recordAllKinds(correction, DATE.plusDays(1), "6");
    for (String table :
        List.of("canonical_stocktake", "canonical_stock_count", "canonical_wastage"))
      assertThat(versions(table)).as(table).isEqualTo(2);
  }

  @Test
  void equalIntermediateAndPreviouslyUnseenOlderReceiptsCannotUndoACorrection() {
    UUID first = raw(1), equal = raw(2), unseen = raw(3), correction = raw(4);
    recordAllKinds(first, DATE, "5");
    recordAllKinds(equal, DATE, "5");
    recordAllKinds(correction, DATE.plusDays(1), "6");
    recordAllKinds(equal, DATE, "5");
    recordAllKinds(unseen, DATE.minusDays(1), "7");
    assertCurrent("6", DATE.plusDays(1));
    for (String table :
        List.of("canonical_stocktake", "canonical_stock_count", "canonical_wastage"))
      assertThat(versions(table)).as(table).isEqualTo(2);
  }

  @Test
  void headerDateCorrectionCanReuseTheLatestDetailButCannotRestoreAnOlderQuantity() {
    UUID header = raw(1), olderDetail = raw(2), latestDetail = raw(3), correctedHeader = raw(4);
    headers.record(header(header, DATE));
    counts.record(count(olderDetail, DATE, "5"));
    counts.record(count(latestDetail, DATE, "6"));
    headers.record(header(correctedHeader, DATE.plusDays(1)));
    counts.record(count(olderDetail, DATE, "5"));
    counts.record(count(latestDetail, DATE, "6"));
    assertThat(query.observations(DATE, DATE, "COUNT", 0, 50)).isEmpty();
    var row = query.observations(DATE.plusDays(1), DATE.plusDays(1), "COUNT", 0, 50).getFirst();
    assertThat(row.quantity()).isEqualByComparingTo("6");
    assertThat(versions("canonical_stock_count")).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select c.header_version_id=h.id from canonical_stock_count c join canonical_stocktake h on h.stocktake_id=c.stocktake_id and h.source_system=c.source_system where c.superseded_at is null and h.superseded_at is null",
                Boolean.class))
        .isTrue();
    headers.record(header(header, DATE));
    counts.record(count(latestDetail, DATE, "6"));
    assertThat(versions("canonical_stock_count")).isEqualTo(3);
    assertThat(query.observations(DATE, DATE, "COUNT", 0, 50)).isEmpty();
  }

  @Test
  void failedBatchRollsBackAcceptedFactsAndAllowsRetry() {
    UUID receipt = raw(1);
    StockCountInput first = count(receipt, DATE, "0");
    StockCountInput invalid =
        new StockCountInput(
            "CTB",
            1,
            2,
            DATE,
            "other",
            BigDecimal.ONE,
            null,
            null,
            "EACH",
            null,
            null,
            null,
            null,
            null,
            UUID.randomUUID());
    assertThatThrownBy(() -> counts.recordAll(List.of(first, invalid)))
        .isInstanceOf(RuntimeException.class);
    assertThat(versions("canonical_stock_count")).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from inventory_observation_receipt", Integer.class))
        .isZero();
    counts.recordAll(List.of(first));
    assertThat(query.observations(DATE, DATE, "COUNT", 0, 50))
        .singleElement()
        .satisfies(row -> assertThat(row.quantity()).isEqualByComparingTo("0"));
  }

  @Test
  void sameTimestampUsesDeterministicReceiptIdOrdering() {
    long prefix = UUID.randomUUID().getMostSignificantBits();
    UUID earlier = raw(1, new UUID(prefix, 1)), later = raw(1, new UUID(prefix, 2));
    recordAllKinds(later, DATE, "6");
    recordAllKinds(earlier, DATE.minusDays(1), "5");
    assertCurrent("6", DATE);
    for (String table :
        List.of("canonical_stocktake", "canonical_stock_count", "canonical_wastage"))
      assertThat(versions(table)).as(table).isEqualTo(1);
  }

  @Test
  void unknownQuantityIsNotReplacedWithAnOldZero() {
    UUID zero = raw(1), unknown = raw(2);
    counts.record(count(zero, DATE, "0"));
    counts.record(count(unknown, DATE, null));
    counts.record(count(zero, DATE, "0"));
    assertThat(query.observations(DATE, DATE, "COUNT", 0, 50))
        .singleElement()
        .satisfies(row -> assertThat(row.quantity()).isNull());
    assertThat(versions("canonical_stock_count")).isEqualTo(2);
  }

  @Test
  void headerMetadataChangeDoesNotManufactureACountRevision() {
    UUID header = raw(1), detail = raw(2), metadata = raw(3);
    headers.record(header(header, DATE));
    counts.record(count(detail, DATE, "5"));
    headers.record(
        new StocktakeInput("CTB", 1, DATE, "Example", "Kitchen", null, true, true, metadata));
    counts.record(count(detail, DATE, "5"));
    counts.record(count(detail, DATE, "5"));
    assertThat(versions("canonical_stock_count")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from inventory_observation_receipt where observation_kind='COUNT'",
                Integer.class))
        .isEqualTo(2);
    assertThat(query.observations(DATE, DATE, "COUNT", 0, 50).getFirst().stocktakeLocked())
        .isTrue();
  }

  @Test
  void v97UpgradeSeedsKnownHistoryWithoutRewritingFactsOrGuessingHeaders() {
    UUID first = raw(1), correction = raw(2);
    recordAllKinds(first, DATE, "5");
    recordAllKinds(correction, DATE.plusDays(1), "6");
    org.flywaydb.core.Flyway.configure()
        .dataSource(jdbc.getDataSource())
        .schemas("inventory_replay_upgrade")
        .defaultSchema("inventory_replay_upgrade")
        .target("97")
        .load()
        .migrate();
    jdbc.update(
        "insert into inventory_replay_upgrade.ingestion_run select * from public.ingestion_run where id in (select ingestion_run_id from public.raw_record where id in (?,?))",
        first,
        correction);
    jdbc.update(
        "insert into inventory_replay_upgrade.raw_record select * from public.raw_record where id in (?,?)",
        first,
        correction);
    for (String table :
        List.of("canonical_stocktake", "canonical_stock_count", "canonical_wastage")) {
      String columns =
          jdbc.queryForObject(
              "select string_agg(quote_ident(column_name),',' order by ordinal_position) from information_schema.columns where table_schema='inventory_replay_upgrade' and table_name=?",
              String.class,
              table);
      jdbc.update(
          "insert into inventory_replay_upgrade."
              + table
              + " ("
              + columns
              + ") select "
              + columns
              + " from public."
              + table);
    }
    var upgrade =
        org.flywaydb.core.Flyway.configure()
            .dataSource(jdbc.getDataSource())
            .schemas("inventory_replay_upgrade")
            .defaultSchema("inventory_replay_upgrade")
            .load();
    upgrade.migrate();
    assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from inventory_replay_upgrade.inventory_observation_receipt",
                Integer.class))
        .isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from inventory_replay_upgrade.canonical_stock_count where header_version_id is null",
                Integer.class))
        .isEqualTo(2);
    for (String table :
        List.of("canonical_stocktake", "canonical_stock_count", "canonical_wastage"))
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from inventory_replay_upgrade." + table, Integer.class))
          .isEqualTo(2);
  }

  private void recordAllKinds(UUID raw, LocalDate date, String qty) {
    headers.record(header(raw, date));
    counts.record(count(raw, date, qty));
    waste.record(
        new WastageInput(
            "CTB",
            1,
            1,
            date,
            "flour",
            new BigDecimal(qty),
            null,
            null,
            null,
            "KG",
            null,
            null,
            raw));
  }

  private StocktakeInput header(UUID raw, LocalDate date) {
    return new StocktakeInput("CTB", 1, date, "Example", "Kitchen", null, true, false, raw);
  }

  private StockCountInput count(UUID raw, LocalDate date, String qty) {
    return new StockCountInput(
        "CTB",
        1,
        1,
        date,
        "flour",
        qty == null ? null : new BigDecimal(qty),
        null,
        "FLOUR",
        "KG",
        null,
        null,
        null,
        "Kitchen",
        null,
        raw);
  }

  private void assertCurrent(String qty, LocalDate date) {
    assertThat(
            jdbc.queryForObject(
                "select stocktake_date from canonical_stocktake where superseded_at is null",
                LocalDate.class))
        .isEqualTo(date);
    assertThat(query.observations(date, date, "ALL", 0, 50))
        .hasSize(2)
        .allSatisfy(row -> assertThat(row.quantity()).isEqualByComparingTo(qty));
  }

  private int versions(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Integer.class);
  }

  private UUID raw(int sequence) {
    return raw(sequence, UUID.randomUUID());
  }

  private UUID raw(int sequence, UUID raw) {
    UUID run = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) values (?,'CTB','test','SUCCESS',now(),1,1)",
        run);
    jdbc.update(
        "insert into raw_record(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,payload_sha256,payload_byte_length,fetcher_identity,fetched_at) values (?,?,'CTB','API','application/json',?, ?,1,'test',?)",
        raw,
        run,
        new byte[] {1},
        "0".repeat(64),
        java.sql.Timestamp.from(Instant.parse("2026-10-12T00:00:00Z").plusSeconds(sequence)));
    return raw;
  }
}
