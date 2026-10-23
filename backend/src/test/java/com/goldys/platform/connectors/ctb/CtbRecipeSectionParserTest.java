package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.goldys.platform.semantic.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class CtbRecipeSectionParserTest {
  @Test
  void acceptsAllRealSurveyResponsesWithoutDroppingScalarFields() throws Exception {
    JsonNode entries;
    try (var input =
        new GZIPInputStream(
            getClass()
                .getResourceAsStream("/fixtures/ctb/recipes/optional-survey.sanitised.har.gz"))) {
      entries = new ObjectMapper().readTree(input).path("log").path("entries");
    }
    Map<String, Set<Integer>> coverage = new HashMap<>();
    int parsed = 0, selected = 0, methods = 0;
    var parser = new CtbRecipeSectionParser();
    for (JsonNode entry : entries) {
      String url = entry.path("request").path("url").asText();
      String section =
          url.endsWith("/GetRecipeByRecipeId")
              ? "HEADER"
              : url.endsWith("/SearchRecipeEquipments")
                  ? "EQUIPMENT"
                  : url.endsWith("/GetRecipeNutritionInformation")
                      ? "NUTRITION"
                      : url.endsWith("/GetRecipeDietaryRequirement")
                          ? "DIETARY"
                          : url.endsWith("/GetRecipeDietaryAllergens") ? "ALLERGENS" : null;
      if (section == null) continue;
      Map<String, String> args = new HashMap<>();
      for (String arg : entry.path("request").path("postData").path("text").asText().split("&")) {
        String[] bits = arg.split("=", 2);
        if (bits.length == 2)
          args.put(
              URLDecoder.decode(bits[0], StandardCharsets.UTF_8),
              URLDecoder.decode(bits[1], StandardCharsets.UTF_8));
      }
      int id = Integer.parseInt(args.get("recipeId"));
      byte[] bytes =
          entry
              .path("response")
              .path("content")
              .path("text")
              .asText()
              .getBytes(StandardCharsets.UTF_8);
      var evidence =
          new RecipeEvidence(
              UUID.randomUUID(),
              "CTB",
              "fixture",
              url,
              null,
              Instant.parse(entry.path("startedDateTime").asText()),
              null,
              "sanitised-test-fixture");
      var result = parser.parse(id, section, bytes, evidence);
      parsed++;
      coverage.computeIfAbsent(section, k -> new HashSet<>()).add(id);
      if (section.equals("DIETARY")
          && result.fields().stream()
              .anyMatch(f -> f.path().endsWith(".isChecked") && "true".equals(f.value())))
        selected++;
      if (section.equals("HEADER")) {
        var desc =
            result.fields().stream()
                .filter(f -> f.path().equals("recipeDesc"))
                .findFirst()
                .orElseThrow();
        if (desc.value() != null && !desc.value().isBlank()) methods++;
        assertThat(desc.value()).doesNotContain("<span", "<pre", "<br>");
      }
      assertThat(result.successful()).isTrue();
      assertThat(result.evidence()).isEqualTo(evidence);
    }
    assertThat(coverage).hasSize(5);
    coverage.values().forEach(ids -> assertThat(ids).hasSize(670));
    assertThat(parsed).isEqualTo(3372);
    assertThat(selected).isEqualTo(3);
    assertThat(methods).isEqualTo(26);
    System.out.printf(
        "Real CTB section hit rate: %d/%d responses parsed; five endpoints cover 670 IDs each; zero rejected responses%n",
        parsed, parsed);
  }

  @Test
  void refusesUnknownPopulatedEquipmentAndMissingSelectedStates() {
    var p = new CtbRecipeSectionParser();
    var e =
        new RecipeEvidence(
            UUID.randomUUID(), "CTB", "test", "test", null, Instant.now(), null, null);
    assertThatThrownBy(
            () ->
                p.parse(
                    1,
                    "EQUIPMENT",
                    "{\"data\":[{\"unknown\":1}]}".getBytes(StandardCharsets.UTF_8),
                    e))
        .hasMessageContaining("not yet verified");
    assertThatThrownBy(
            () ->
                p.parse(
                    1,
                    "DIETARY",
                    "{\"data\":[{\"dietaryRequirementId\":1}]}".getBytes(StandardCharsets.UTF_8),
                    e))
        .hasMessageContaining("selected state");
  }
}
