package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.canonical.SaleRecipeLinkInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbSaleRecipeLinkParserTest {

  private static final UUID RAW = UUID.randomUUID();
  private final CtbSaleRecipeLinkParser parser = new CtbSaleRecipeLinkParser();

  @Test
  void parsesLinkFields() {
    byte[] json =
        ("{\"data\":[{\"saleItemRecipeId\":9001,\"saleItemStockCode\":\"STOCK-1\","
                + "\"saleItemStockDescription\":\"Kids Fish\",\"recipeId\":627,\"recipeName\":\"Kids Fish\","
                + "\"recipeFolderName\":\"Food\",\"isRecipeActive\":true}]}")
            .getBytes(StandardCharsets.UTF_8);

    List<SaleRecipeLinkInput> out = parser.parse(json, RAW);

    assertThat(out).hasSize(1);
    SaleRecipeLinkInput l = out.get(0);
    assertThat(l.sourceSystem()).isEqualTo("CTB");
    assertThat(l.saleItemRecipeId()).isEqualTo(9001);
    assertThat(l.saleItemStockCode()).isEqualTo("STOCK-1");
    assertThat(l.saleItemStockDescription()).isEqualTo("Kids Fish");
    assertThat(l.recipeId()).isEqualTo(627);
    assertThat(l.recipeName()).isEqualTo("Kids Fish");
    assertThat(l.recipeFolderName()).isEqualTo("Food");
    assertThat(l.isRecipeActive()).isTrue();
    assertThat(l.rawRecordId()).isEqualTo(RAW);
  }

  @Test
  void missingActiveFlagIsNull() {
    byte[] json =
        "{\"data\":[{\"saleItemRecipeId\":9001,\"saleItemStockDescription\":\"Kids Fish\",\"recipeId\":627}]}"
            .getBytes(StandardCharsets.UTF_8);

    SaleRecipeLinkInput l = parser.parse(json, RAW).get(0);

    assertThat(l.isRecipeActive()).isNull();
  }

  @Test
  void stripsNullEscapes() {
    String nul = "\\u" + "0000";
    byte[] json =
        ("{\"data\":[{\"saleItemRecipeId\":9001,\"recipeName\":\"Kids"
                + nul
                + " Fish\",\"recipeId\":627}]}")
            .getBytes(StandardCharsets.UTF_8);

    SaleRecipeLinkInput l = parser.parse(json, RAW).get(0);

    assertThat(l.recipeName()).isEqualTo("Kids Fish");
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
