package com.goldys.platform.connectors.ctb;

import static com.goldys.platform.support.RecipeFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CtbRecipeDefinitionParserTest {
  private final CtbRecipeDefinitionParser parser = new CtbRecipeDefinitionParser();

  @Test
  void allNineteenRealCapturesParseWithoutDroppingLines() {
    int samples = 0, stocks = 0, usages = 0;
    for (var sample : manifest("manifest.json")) {
      byte[] s = bytes(sample.path("stocks").asText()), u = bytes(sample.path("usages").asText());
      var definition =
          parser.parse(
              sample.path("recipeId").asInt(),
              bytes(sample.path("header").asText()),
              s,
              u,
              Instant.parse(sample.path("observedAt").asText()));
      assertThat(definition.stocks()).hasSize(CtbRecipeDefinitionParser.array(s).size());
      assertThat(definition.usages()).hasSize(CtbRecipeDefinitionParser.array(u).size());
      java.math.BigDecimal componentTotal = java.math.BigDecimal.ZERO;
      for (var line : definition.stocks()) componentTotal = componentTotal.add(line.cost());
      for (var line : definition.usages()) componentTotal = componentTotal.add(line.cost());
      assertThat(componentTotal.subtract(definition.header().totalCost()).abs())
          .isLessThan(new java.math.BigDecimal("0.01"));
      stocks += definition.stocks().size();
      usages += definition.usages().size();
      samples++;
    }
    assertThat(samples).isEqualTo(19);
    System.out.printf(
        "Real HAR coverage: %d/%d complete captures; %d direct stock lines; %d sub-recipe usages; 0 rejected captures%n",
        samples, samples, stocks, usages);
  }

  @Test
  void signedAdjustmentsBatchPortionsAndStockUomArePreserved() {
    var chips = definition("third_651");
    assertThat(chips.stocks().getFirst().quantity()).isEqualByComparingTo("-200");
    assertThat(chips.stocks().getFirst().cost()).isEqualByComparingTo("-0.94");
    assertThat(chips.stocks().getFirst().stockUnit()).isEqualTo("kg");
    var vegan = definition("third_643");
    assertThat(vegan.usages().getFirst().convertedQuantity()).isEqualByComparingTo("-0.05");
    assertThat(vegan.stocks().getFirst().quantity()).isEqualByComparingTo("50");
    var corn = definition("third_696");
    assertThat(corn.header().portionCount()).isEqualByComparingTo("15");
    assertThat(corn.header().portionSize()).isEqualByComparingTo("1");
    assertThat(definition("second_451").header().portionSize()).isEqualByComparingTo("6");
    assertThat(definition("third_719").header().recipeId())
        .isNotEqualTo(definition("third_721").header().recipeId());
  }

  @Test
  void missingArrayFailureEnvelopeAndMismatchedOwnersAreNotEmptySnapshots() {
    byte[] header = bytes("third_651_header.json"),
        stock = bytes("third_651_stocks.json"),
        usage = bytes("third_651_usages.json");
    assertThatThrownBy(
            () ->
                parser.parse(
                    651, header, "{}".getBytes(StandardCharsets.UTF_8), usage, Instant.now()))
        .isInstanceOf(ConnectorFetchException.class);
    assertThatThrownBy(() -> parser.parse(652, header, stock, usage, Instant.now()))
        .isInstanceOf(ConnectorFetchException.class);
    assertThatThrownBy(
            () ->
                parser.parse(
                    651,
                    header,
                    stock,
                    "{\"message\":{\"IsSuccess\":false},\"data\":[]}"
                        .getBytes(StandardCharsets.UTF_8),
                    Instant.now()))
        .isInstanceOf(ConnectorFetchException.class);
  }

  @Test
  void realPartialFolderWalksDoNotMasqueradeAsCompleteCatalogs() {
    CtbRecipeCatalogParser tree = new CtbRecipeCatalogParser();
    for (var sample : manifest("folders_manifest.json")) {
      if (sample.path("file").asText().startsWith("second_"))
        tree.accept(sample.path("folderId").asInt(), bytes(sample.path("file").asText()));
    }
    assertThat(tree.pending()).isNotEmpty();
    assertThatThrownBy(() -> tree.finish(Instant.now()))
        .isInstanceOf(ConnectorFetchException.class);
  }
}
