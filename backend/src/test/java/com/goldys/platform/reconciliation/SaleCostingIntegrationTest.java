package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.*;
import com.goldys.platform.ingestion.*;
import com.goldys.platform.semantic.*;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class SaleCostingIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalSaleItemIngest ingest;
  @Autowired IngestionService ingestion;
  @Autowired SaleCostingQuery query;
  @MockitoBean RecipeMetricsQuery recipes;
  static final LocalDate DAY = LocalDate.of(2026, 10, 21);

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_sale_item, canonical_sale_recipe_link");
  }

  private UUID raw() {
    return ingestion.ingestPush(
        "LIGHTSPEED",
        "sale-cost-test",
        FetchMethod.FILE_EXPORT,
        "text/csv",
        new byte[] {1},
        "UTF-8",
        "test");
  }

  private void line(String id, String name, BigDecimal quantity) {
    ingest.record(
        new SaleItemInput(
            "LIGHTSPEED",
            id,
            DAY,
            "SP-100 1021082623",
            name,
            null,
            null,
            null,
            quantity,
            new BigDecimal("28"),
            new BigDecimal("28"),
            null,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null,
            null,
            raw(),
            null));
  }

  private RecipeCosting recipe(int id, String name, String cost, boolean stale) {
    return new RecipeCosting(
        id,
        name,
        null,
        "1",
        cost == null ? null : new BigDecimal(cost),
        null,
        null,
        null,
        null,
        null,
        "UNKNOWN",
        "NONE",
        null,
        null,
        null,
        Instant.parse("2026-10-23T00:00:00Z"),
        "CTB complete refresh",
        "COMPLETE",
        stale);
  }

  @Test
  void scalesCurrentRecipeObservationsWithoutInventingTaxComparableProfit() {
    line("steak", "$28 Steak Frites", new BigDecimal("2"));
    // Synthetic zero is deliberate: test verified zero recipe cost, not the POS zero placeholder.
    when(recipes.costing()).thenReturn(List.of(recipe(722, "$28 Steak Frites", "0", false)));
    var result = query.costing("SP-100 1021082623", DAY, DAY);
    assertThat(result.complete()).isTrue();
    assertThat(result.lines().getFirst().recipeId()).isEqualTo(722);
    assertThat(result.recipeCost()).isZero();
    assertThat(result.margin()).isNull();
    assertThat(result.grossProfit()).isNull();
    assertThat(result.qualification()).isEqualTo("TAX_BASIS_UNVERIFIED");
    when(recipes.costing()).thenReturn(List.of(recipe(722, "$28 Steak Frites", "8.50", false)));
    assertThat(query.costing("SP-100 1021082623", DAY, DAY).recipeCost())
        .isEqualByComparingTo("17.00");
    line("steak", "$28 Steak Frites", new BigDecimal("-1"));
    assertThat(query.costing("SP-100 1021082623", DAY, DAY).recipeCost())
        .isEqualByComparingTo("-8.50");
  }

  @Test
  void withholdsTotalsForMissingStaleOrAmbiguousCosts() {
    line("steak", "$28 Steak Frites", BigDecimal.ONE);
    for (var r :
        List.of(
            recipe(722, "$28 Steak Frites", null, false),
            recipe(722, "$28 Steak Frites", "8.50", true))) {
      when(recipes.costing()).thenReturn(List.of(r));
      var result = query.costing("SP-100 1021082623", DAY, DAY);
      assertThat(result.complete()).isFalse();
      assertThat(result.recipeCost()).isNull();
    }
    when(recipes.costing())
        .thenReturn(
            List.of(
                recipe(722, "$28 Steak Frites", "8.50", false),
                recipe(723, "$28 Steak Frites", "9", false)));
    assertThat(query.costing("SP-100 1021082623", DAY, DAY).lines().getFirst().status())
        .isEqualTo("AMBIGUOUS_NAME");
    assertThat(query.costing("ANOTHER-SALE", DAY, DAY).lines()).isEmpty();
  }
}
