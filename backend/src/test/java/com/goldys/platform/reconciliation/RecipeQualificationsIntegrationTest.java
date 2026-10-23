package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.RecipeIngredientIngest;
import com.goldys.platform.canonical.RecipeIngredientInput;
import com.goldys.platform.semantic.RecipeIngredient;
import com.goldys.platform.semantic.RecipeIngredientMetricsQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
@Transactional
class RecipeQualificationsIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired RecipeIngredientIngest ingest;
  @Autowired RecipeIngredientMetricsQuery query;

  @Test
  void projectsCanonicalQualificationsAndKeepsRepeatedDetailIdsSeparate() {
    UUID run = UUID.randomUUID();
    UUID raw = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)",
        run);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) values (?, ?, 'CTB', 'FILE_EXPORT', 'text/csv', ?, ?, 1, 'test', now())",
        raw,
        run,
        new byte[] {1},
        "0".repeat(64));
    ingest.record(input(raw, 401, BigDecimal.ZERO, true));
    ingest.record(input(raw, 402, null, null));
    var rows = query.ingredientsFor(89101);
    assertThat(rows).hasSize(2);
    RecipeIngredient first =
        rows.stream().filter(row -> row.stockId() == 401).findFirst().orElseThrow();
    assertThat(first.stockUomShortname()).isEqualTo("kg");
    assertThat(first.stockUsable()).isEqualByComparingTo("0");
    assertThat(first.stockWastage()).isNull();
    assertThat(first.isBrokenLink()).isTrue();
    RecipeIngredient second =
        rows.stream().filter(row -> row.stockId() == 402).findFirst().orElseThrow();
    assertThat(second.stockUsable()).isNull();
    assertThat(second.isBrokenLink()).isNull();
  }

  private RecipeIngredientInput input(UUID raw, int stockId, BigDecimal usable, Boolean broken) {
    return new RecipeIngredientInput(
        "CTB",
        89101,
        89101,
        "Test recipe",
        false,
        100,
        stockId,
        "STOCK-" + stockId,
        "Ingredient",
        "Ingredient",
        "Supplier",
        "S-" + stockId,
        BigDecimal.ONE,
        "grams",
        "kg",
        null,
        null,
        usable,
        null,
        broken,
        raw);
  }
}
