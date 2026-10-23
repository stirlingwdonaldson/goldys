package com.goldys.platform.reconciliation;

import static com.goldys.platform.support.RecipeFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.canonical.RecipeDefinitionIngest;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.semantic.*;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"ctb.recipes.enabled=true", "app.scheduling.ctb.enabled=false"})
@Import(PostgresContainerConfiguration.class)
class RecipeDefinitionIntegrationTest {
  @Autowired com.goldys.platform.connectors.ctb.CtbConnector connector;
  @Autowired RecipeDefinitionIngest ingest;
  @Autowired IngestionService ingestion;
  @Autowired RecipeCompositionQuery query;
  @Autowired RecipeMetricsQuery costing;
  @Autowired RecipeIngredientMetricsQuery ingredients;
  @Autowired RecipeDefinitionProjector projector;
  @Autowired JdbcTemplate jdbc;

  @Test
  void completeSnapshotsRemoveLinesPreserveHistoryAndRejectOlderReplay() {
    assertThat(
            org.springframework.test.util.ReflectionTestUtils.getField(connector, "recipeRefresh"))
        .isNotNull();
    var captured = definition("third_651");
    UUID h = raw("third_651_header.json"),
        s = raw("third_651_stocks.json"),
        u = raw("third_651_usages.json");
    var initial =
        new RecipeDefinition(
            captured.header(),
            captured.stocks(),
            captured.usages(),
            Instant.parse("2026-10-23T02:00:00Z"));
    ingest.record(initial, h, s, u);
    assertThat(query.composition(651).orElseThrow().definition().stocks()).hasSize(1);
    assertThat(ingredients.ingredientsFor(651).getFirst().portionWeight())
        .isEqualByComparingTo("-200");
    assertThat(
            costing.costing().stream()
                .filter(r -> r.recipeId() == 651)
                .findFirst()
                .orElseThrow()
                .costPerPortion())
        .isEqualByComparingTo("-0.94");
    var empty =
        new RecipeDefinition(
            captured.header(), List.of(), List.of(), initial.observedAt().plusSeconds(60));
    ingest.record(
        empty,
        raw("third_651_header.json"),
        raw("third_639_stocks.json"),
        raw("third_639_usages.json"));
    assertThat(query.composition(651).orElseThrow().definition().stocks()).isEmpty();
    assertThat(ingredients.ingredientsFor(651)).isEmpty();
    ingest.record(initial, h, s, u);
    assertThat(query.composition(651).orElseThrow().definition().observedAt())
        .isEqualTo(empty.observedAt());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_recipe_definition where recipe_id=651",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_recipe_definition_stocks", Integer.class))
        .isGreaterThanOrEqualTo(1);
    projector.recomputeAll();
    assertThat(query.composition(651).orElseThrow().definition().stocks()).isEmpty();
    assertThat(query.composition(651).orElseThrow().definition().observedAt())
        .isEqualTo(empty.observedAt());
  }

  @Test
  void catalogRetainsEmptyFoldersExplicitAllocationsAndRebuilds() {
    var catalog =
        new RecipeCatalog(
            List.of(new RecipeFolder(0, null, "Recipes"), new RecipeFolder(777, 0, "Empty prep")),
            List.of(new RecipeOutletAllocation("folder", 777, 2, true)),
            Instant.parse("2026-10-23T03:00:00Z"));
    ingest.recordCatalog(catalog, List.of(raw("second_folder_139.json")));
    assertThat(query.catalog().orElseThrow().folders())
        .containsExactlyElementsOf(catalog.folders());
    projector.recomputeAll();
    assertThat(query.catalog().orElseThrow().allocations().getFirst().deallocated()).isTrue();
  }

  private UUID raw(String file) {
    return ingestion.ingestPush(
        "CTB",
        "recipe-fixture-test",
        FetchMethod.API,
        "application/json",
        bytes(file),
        "UTF-8",
        "recipe-fixture-test");
  }
}
