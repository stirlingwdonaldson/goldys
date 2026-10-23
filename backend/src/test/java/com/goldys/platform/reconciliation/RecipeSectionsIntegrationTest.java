package com.goldys.platform.reconciliation;

import static com.goldys.platform.support.RecipeFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.goldys.platform.canonical.*;
import com.goldys.platform.connectors.ctb.CtbRecipeSectionParser;
import com.goldys.platform.ingestion.*;
import com.goldys.platform.semantic.*;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class RecipeSectionsIntegrationTest {
  @Autowired RecipeSectionIngest ingest;
  @Autowired RecipeDefinitionIngest definitions;
  @Autowired IngestionService ingestion;
  @Autowired RawLedgerQuery raw;
  @Autowired RecipeCompositionQuery query;
  @Autowired RecipeMetricsQuery recipes;
  @Autowired RecipeSectionProjector projector;
  @Autowired JdbcTemplate jdbc;
  private final CtbRecipeSectionParser parser = new CtbRecipeSectionParser();

  @Test
  void realSectionsRetainEvidenceHistoryEmptyValuesAndFailureWhileInventoryIncludesMissingDetails()
      throws Exception {
    JsonNode entries;
    try (var input =
        new GZIPInputStream(
            getClass()
                .getResourceAsStream("/fixtures/ctb/recipes/optional-survey.sanitised.har.gz"))) {
      entries = new ObjectMapper().readTree(input).path("log").path("entries");
    }
    for (JsonNode entry : entries) {
      String endpoint = entry.path("request").path("url").asText();
      boolean list = endpoint.endsWith("/GetAllRecipes");
      String args = entry.path("request").path("postData").path("text").asText();
      boolean dietary =
          endpoint.endsWith("/GetRecipeDietaryRequirement") && args.contains("recipeId=377");
      if (!list && !dietary) continue;
      byte[] bytes =
          entry
              .path("response")
              .path("content")
              .path("text")
              .asText()
              .getBytes(StandardCharsets.UTF_8);
      var section = section(dietary ? 377 : 0, dietary ? "DIETARY" : "LIST", bytes, endpoint);
      ingest.record(section);
    }
    var observed =
        query.details(377).orElseThrow().sections().stream()
            .filter(s -> s.section().equals("DIETARY"))
            .findFirst()
            .orElseThrow();
    assertThat(observed.fields())
        .anySatisfy(
            f -> {
              assertThat(f.path()).endsWith(".isChecked");
              assertThat(f.value()).isEqualTo("true");
            });
    UUID failedRaw = store("{\"message\":{\"IsSuccess\":false}}".getBytes(StandardCharsets.UTF_8));
    var failure =
        new RecipeSection(
            377,
            "DIETARY",
            false,
            "Source response unsuccessful",
            List.of(),
            raw.recipeEvidence(failedRaw, "/DietaryRequirement/GetRecipeDietaryRequirement"));
    ingest.record(failure);
    var details = query.details(377).orElseThrow();
    assertThat(details.sections())
        .anySatisfy(
            s -> {
              assertThat(s.section()).isEqualTo("DIETARY");
              assertThat(s.fields()).isEqualTo(observed.fields());
              assertThat(s.evidence().rawRecordId()).isEqualTo(observed.evidence().rawRecordId());
            });
    assertThat(details.latestAttempts())
        .anySatisfy(
            s -> {
              assertThat(s.section()).isEqualTo("DIETARY");
              assertThat(s.successful()).isFalse();
              assertThat(s.evidence().rawRecordId()).isEqualTo(failedRaw);
            });
    int versions =
        jdbc.queryForObject(
            "select count(*) from canonical_recipe_section where recipe_id=377 and section='DIETARY'",
            Integer.class);
    ingest.record(observed); // Repeated evidence must not create another canonical version.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_recipe_section where recipe_id=377 and section='DIETARY'",
                Integer.class))
        .isEqualTo(versions);
    projector.recomputeAll();
    assertThat(query.details(377).orElseThrow().sections()).contains(observed);
    assertThat(recipes.costing())
        .anySatisfy(
            v -> {
              assertThat(v.recipeId()).isEqualTo(728);
              assertThat(v.coverage()).isEqualTo("DETAILS_MISSING");
              assertThat(v.costPerPortion()).isNull();
            });
    assertThat(query.details(728).orElseThrow().name()).isEqualTo("Cprase eggplant 2026");
  }

  @Test
  void relationshipsUseIdsInBothDirectionsAndCalculationEvidenceIncludesDescendants() {
    capture("second_464");
    capture("second_463");
    var parent = query.details(464).orElseThrow();
    var child = query.details(463).orElseThrow();
    assertThat(parent.uses()).anySatisfy(v -> assertThat(v.recipeId()).isEqualTo(463));
    assertThat(child.usedIn()).anySatisfy(v -> assertThat(v.recipeId()).isEqualTo(464));
    assertThat(child.uses())
        .anySatisfy(
            v -> {
              assertThat(v.recipeId()).isEqualTo(462);
              assertThat(v.available()).isFalse();
            });
    assertThat(parent.compositionSources())
        .hasSize(6)
        .allSatisfy(
            e -> {
              assertThat(e.rawRecordId()).isNotNull();
              assertThat(e.sha256()).hasSize(64);
              assertThat(e.ingestionRunId()).isNotNull();
            });
  }

  private void capture(String prefix) {
    var value = definition(prefix);
    int id = value.header().recipeId();
    var h =
        section(id, "HEADER", bytes(prefix + "_header.json"), "/RecipeBook/GetRecipeByRecipeId");
    var s =
        section(
            id, "STOCKS", bytes(prefix + "_stocks.json"), "/RecipeBook/GetRecipeDetailByRecipeId");
    var u =
        section(
            id,
            "USAGES",
            bytes(prefix + "_usages.json"),
            "/RecipeBook/GetIngredientFromExistingRecipesByRecipeId");
    definitions.record(
        new RecipeDefinition(
            value.header(), value.stocks(), value.usages(), h.evidence().capturedAt()),
        h.evidence().rawRecordId(),
        s.evidence().rawRecordId(),
        u.evidence().rawRecordId());
    ingest.record(h);
    ingest.record(s);
    ingest.record(u);
  }

  private RecipeSection section(int id, String name, byte[] bytes, String endpoint) {
    UUID rawId = store(bytes);
    return parser.parse(id, name, bytes, raw.recipeEvidence(rawId, endpoint));
  }

  private UUID store(byte[] bytes) {
    return ingestion.ingestPush(
        "CTB",
        "recipe-section-test",
        FetchMethod.API,
        "application/json",
        bytes,
        "UTF-8",
        "recipe-section-test");
  }
}
