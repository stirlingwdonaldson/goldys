package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.canonical.RecipeCostInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbRecipeCostParserTest {

  private static final UUID RAW = UUID.randomUUID();
  private final CtbRecipeCostParser parser = new CtbRecipeCostParser();

  @Test
  void parsesRecipeCostFields() {
    byte[] json =
        ("{\"data\":[{\"recipeId\":627,\"recipeName\":\"Parma\",\"costPerPortion\":6.00,"
                + "\"costPerPortionIncludingLabour\":6.50,\"totalCost\":13.00,\"portionSize\":\"4\"}]}")
            .getBytes(StandardCharsets.UTF_8);

    List<RecipeCostInput> out = parser.parse(json, RAW);

    assertThat(out).hasSize(1);
    RecipeCostInput r = out.get(0);
    assertThat(r.sourceSystem()).isEqualTo("CTB");
    assertThat(r.recipeId()).isEqualTo(627);
    assertThat(r.recipeName()).isEqualTo("Parma");
    assertThat(r.costPerPortion()).isEqualByComparingTo("6.00");
    assertThat(r.costPerPortionInclLabour()).isEqualByComparingTo("6.50");
    assertThat(r.totalCost()).isEqualByComparingTo("13.00");
    assertThat(r.portionSize()).isEqualTo("4");
    assertThat(r.portionSizeValue()).isEqualByComparingTo("4.0000");
    assertThat(r.rawRecordId()).isEqualTo(RAW);
  }

  @Test
  void portionSizeNonNumericYieldsNullValue() {
    byte[] json =
        "{\"data\":[{\"recipeId\":1,\"recipeName\":\"Parma\",\"portionSize\":\"1 serve\"}]}"
            .getBytes(StandardCharsets.UTF_8);

    RecipeCostInput r = parser.parse(json, RAW).get(0);

    assertThat(r.portionSize()).isEqualTo("1 serve");
    assertThat(r.portionSizeValue()).isNull();
  }

  @Test
  void portionSizeNumericYieldsParsedValue() {
    byte[] json =
        "{\"data\":[{\"recipeId\":1,\"recipeName\":\"Parma\",\"portionSize\":\"4\"}]}"
            .getBytes(StandardCharsets.UTF_8);

    RecipeCostInput r = parser.parse(json, RAW).get(0);

    assertThat(r.portionSizeValue()).isEqualByComparingTo("4.0000");
  }

  @Test
  void stripsNullEscapes() {
    String nul = "\\u" + "0000";
    byte[] json =
        ("{\"data\":[{\"recipeId\":1,\"recipeName\":\"Parma" + nul + " and chips\"}]}")
            .getBytes(StandardCharsets.UTF_8);

    RecipeCostInput r = parser.parse(json, RAW).get(0);

    assertThat(r.recipeName()).isEqualTo("Parma and chips");
    assertThat(r.recipeName()).doesNotContain("\u0000");
  }

  @Test
  void nonJsonThrowsSchemaMismatch() {
    assertThatThrownBy(() -> parser.parse("not json".getBytes(StandardCharsets.UTF_8), RAW))
        .isInstanceOf(ConnectorFetchException.class)
        .satisfies(
            e ->
                assertThat(((ConnectorFetchException) e).failureType())
                    .isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }
}
