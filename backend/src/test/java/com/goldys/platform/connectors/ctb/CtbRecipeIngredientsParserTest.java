package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.canonical.RecipeIngredientInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbRecipeIngredientsParserTest {

  private static final UUID RAW = UUID.randomUUID();
  private final CtbRecipeIngredientsParser parser = new CtbRecipeIngredientsParser();

  @Test
  void parsesIngredientFields() {
    String json =
        """
        {"data":[{"recipeDetailId":101,"recipeId":463,"recipeName":"Cheese Burger","stockId":42,
        "stockCode":"CHICKEN-1","stockDesc":"Chicken breast","ingredientDescription":"Chicken breast",
        "supplierName":"Acme","supplierStockCode":"ACME-1","portionWeight":150,
        "unitOfMeasurementName":"grams","stockUomShortname":"g","costPerQty":6.00,"costPerUnit":2.00,
        "stockUsable":1.00,"stockWastage":0.10,"isSubRecipeIngredient":false,"isBrokenLink":false}],
        "message":{"IsSuccess":true},"total":1}
        """;

    List<RecipeIngredientInput> out = parser.parse(bytes(json), RAW, 463);

    assertThat(out).hasSize(1);
    RecipeIngredientInput line = out.get(0);
    assertThat(line.parentRecipeId()).isEqualTo(463);
    assertThat(line.lineRecipeId()).isEqualTo(463);
    assertThat(line.lineRecipeName()).isEqualTo("Cheese Burger");
    assertThat(line.isSubRecipe()).isFalse();
    assertThat(line.recipeDetailId()).isEqualTo(101);
    assertThat(line.stockId()).isEqualTo(42);
    assertThat(line.stockCode()).isEqualTo("CHICKEN-1");
    assertThat(line.stockDescription()).isEqualTo("Chicken breast");
    assertThat(line.portionWeight()).isEqualByComparingTo("150");
    assertThat(line.unitOfMeasurement()).isEqualTo("grams");
    assertThat(line.costPerQty()).isEqualByComparingTo("6.00");
    assertThat(line.supplierName()).isEqualTo("Acme");
  }

  @Test
  void parentRecipeIdComesFromTheArgumentNotThePayload() {
    // A sub-recipe line's `recipeId` is the sub-recipe, but the payload belongs to the parent.
    String json =
        """
        {"data":[{"recipeDetailId":201,"recipeId":900,"recipeName":"Aioli","stockId":7,
        "stockDesc":"Garlic","isSubRecipeIngredient":true}],"message":{"IsSuccess":true},"total":1}
        """;

    List<RecipeIngredientInput> out = parser.parse(bytes(json), RAW, 463);

    assertThat(out).hasSize(1);
    assertThat(out.get(0).parentRecipeId()).isEqualTo(463);
    assertThat(out.get(0).lineRecipeId()).isEqualTo(900);
    assertThat(out.get(0).isSubRecipe()).isTrue();
  }

  @Test
  void stripsNullEscapes() {
    String nul = "\\u" + "0000";
    String json =
        "{\"data\":[{\"recipeDetailId\":1,\"recipeId\":1,\"recipeName\":\"Parma"
            + nul
            + "\","
            + "\"stockDesc\":\"Oil"
            + nul
            + "\"}],\"message\":{\"IsSuccess\":true},\"total\":1}";

    List<RecipeIngredientInput> out = parser.parse(bytes(json), RAW, 1);

    assertThat(out.get(0).lineRecipeName()).isEqualTo("Parma");
    assertThat(out.get(0).lineRecipeName()).doesNotContain("\u0000");
    assertThat(out.get(0).stockDescription()).isEqualTo("Oil");
  }

  @Test
  void dedupesExactDuplicateLines() {
    String one =
        """
        {"recipeDetailId":617,"recipeId":617,"recipeName":"Pickle Brine","stockId":5,
        "stockDesc":"Vinegar","portionWeight":100,"isSubRecipeIngredient":false}
        """;
    String json =
        "{\"data\":["
            + one
            + ","
            + one
            + ","
            + one
            + ","
            + one
            + "],"
            + "\"message\":{\"IsSuccess\":true},\"total\":4}";

    List<RecipeIngredientInput> out = parser.parse(bytes(json), RAW, 617);

    assertThat(out).hasSize(1);
  }

  @Test
  void nonNumericQuantityIsNull() {
    String json =
        """
        {"data":[{"recipeDetailId":1,"recipeId":1,"stockId":1,"portionWeight":"about 100g",
        "costPerQty":"garbage"}],"message":{"IsSuccess":true},"total":1}
        """;

    List<RecipeIngredientInput> out = parser.parse(bytes(json), RAW, 1);

    assertThat(out.get(0).portionWeight()).isNull();
    assertThat(out.get(0).costPerQty()).isNull();
  }

  @Test
  void sentinelStockIdIsNull() {
    String json =
        """
        {"data":[{"recipeDetailId":1,"recipeId":1,"stockId":0,"stockDesc":"Oil"},
        {"recipeDetailId":2,"recipeId":1,"stockId":-1,"stockDesc":"Salt"}],
        "message":{"IsSuccess":true},"total":2}
        """;

    List<RecipeIngredientInput> out = parser.parse(bytes(json), RAW, 1);

    assertThat(out).extracting(RecipeIngredientInput::stockId).containsOnlyNulls();
  }

  @Test
  void nonJsonThrowsSchemaMismatch() {
    assertThatThrownBy(() -> parser.parse(bytes("not json"), RAW, 1))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");
  }

  @Test
  void missingDataArrayReturnsEmpty() {
    assertThat(parser.parse(bytes("{\"message\":{\"IsSuccess\":false}}"), RAW, 1)).isEmpty();
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
