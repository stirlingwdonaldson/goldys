package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.*;
import com.goldys.platform.semantic.InventoryObservationQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class InventoryObservationIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalStockCountIngest counts;
  @Autowired CanonicalStocktakeIngest headers;
  @Autowired CanonicalWastageIngest waste;
  @Autowired InventoryObservationQuery query;
  final LocalDate date = LocalDate.of(2026, 10, 5);

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate inventory_observation_receipt,canonical_stock_count,canonical_stocktake,canonical_wastage");
  }

  @Test
  void datedCountAndWastePreserveUnitsUnknownsAndExplicitZero() {
    UUID raw = raw();
    headers.record(
        new StocktakeInput("CTB", 1, date, "Example", "Kitchen", null, true, false, raw));
    counts.record(count(date, "0", raw));
    waste.record(
        new WastageInput(
            "CTB",
            2,
            5,
            date,
            "flour",
            new BigDecimal("2"),
            null,
            null,
            null,
            "EACH",
            "pack",
            null,
            raw));
    var rows = query.observations(date, date, "ALL", 0, 50);
    assertThat(rows).hasSize(2);
    var count = rows.stream().filter(r -> r.kind().equals("COUNT")).findFirst().orElseThrow();
    assertThat(count.quantity()).isEqualByComparingTo("0");
    assertThat(count.unit()).isEqualTo("KG");
    assertThat(count.stocktakePartial()).isTrue();
    assertThat(count.stocktakeLocked()).isFalse();
    assertThat(count.sourceValue()).isNull();
    assertThat(rows.stream().filter(r -> r.kind().equals("WASTE")).findFirst().orElseThrow().unit())
        .isEqualTo("EACH");
    assertThat(query.observations(date, date, "ALL", 0, 1)).hasSize(1);
    assertThat(query.observations(date, date, "ALL", 1, 1)).hasSize(1);
    assertThat(query.observations(date, date, "COUNT", 0, 50)).hasSize(1);
    assertThat(query.observations(date.plusDays(1), date.plusDays(1), "ALL", 0, 50)).isEmpty();
  }

  /**
   * CTB's wastage {@code cost} is per stock unit, so a waste row reports it as {@code unitCost} and
   * its {@code sourceValue} is quantity x cost (the per-line value CTB itself displays).
   */
  @Test
  void wasteRowReportsUnitCostAndLineValue() {
    waste.record(
        new WastageInput(
            "CTB",
            149,
            727,
            date,
            "chip supa crunch 10mm uf 6x2k",
            new BigDecimal("0.5"),
            new BigDecimal("56.65"),
            null,
            "Sealane food services",
            "kilograms",
            "12 kilograms",
            null,
            raw()));

    var row = query.observations(date, date, "WASTE", 0, 50).getFirst();

    assertThat(row.unitCost()).isEqualByComparingTo("56.65");
    assertThat(row.sourceValue()).isEqualByComparingTo("28.325");
  }

  @Test
  void wasteWithoutCostHasNoValue() {
    waste.record(
        new WastageInput(
            "CTB",
            150,
            1,
            date,
            "oil",
            new BigDecimal("1"),
            null,
            null,
            null,
            "EACH",
            null,
            null,
            raw()));

    var row = query.observations(date, date, "WASTE", 0, 50).getFirst();

    assertThat(row.unitCost()).isNull();
    assertThat(row.sourceValue()).isNull();
  }

  @Test
  void correctionMovesTheObservationWithoutRewritingHistoryAndUnknownHeaderStaysUnknown() {
    counts.record(count(date, "5", raw()));
    counts.record(count(date.plusDays(1), "6", raw()));
    assertThat(query.observations(date, date, "ALL", 0, 50)).isEmpty();
    var row = query.observations(date.plusDays(1), date.plusDays(1), "ALL", 0, 50).getFirst();
    assertThat(row.quantity()).isEqualByComparingTo("6");
    assertThat(row.stocktakePartial()).isNull();
    assertThat(jdbc.queryForObject("select count(*) from canonical_stock_count", Integer.class))
        .isEqualTo(2);
  }

  private StockCountInput count(LocalDate d, String qty, UUID raw) {
    return new StockCountInput(
        "CTB",
        1,
        1,
        d,
        "flour",
        new BigDecimal(qty),
        42,
        "FLOUR",
        "KG",
        null,
        null,
        null,
        "Kitchen",
        null,
        raw);
  }

  private UUID raw() {
    UUID run = UUID.randomUUID(), raw = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) values (?,'CTB','test','SUCCESS',now(),1,1)",
        run);
    jdbc.update(
        "insert into raw_record(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,payload_sha256,payload_byte_length,fetcher_identity,fetched_at) values (?,?,'CTB','API','application/json',?, ?,1,'test',now())",
        raw,
        run,
        new byte[] {1},
        "0".repeat(64));
    return raw;
  }
}
