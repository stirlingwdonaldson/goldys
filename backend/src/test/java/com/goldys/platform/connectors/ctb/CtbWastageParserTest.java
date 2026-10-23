package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.WastageInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbWastageParserTest {

  private static final UUID RAW = UUID.randomUUID();
  private final CtbWastageParser parser = new CtbWastageParser();

  @Test
  void parsesWastageFields() {
    String json =
        """
        {"data":[{"wastageRecordId":7,"wastageRecordDetailId":359,"recordDate":"2026-10-05",
        "stockDesc":"Chicken breast","quantity":3,"cost":12.00,"reason":"Spoilage",
        "supplierName":"Acme","unitOfMeasurementName":"kg","unitSize":"1kg","recipeId":105}],
        "message":{"IsSuccess":true}}
        """;

    List<WastageInput> out = parser.parse(bytes(json), RAW);

    assertThat(out).hasSize(1);
    WastageInput w = out.get(0);
    assertThat(w.wastageRecordId()).isEqualTo(7);
    assertThat(w.lineId()).isEqualTo(359);
    assertThat(w.wastageDate()).isEqualTo(LocalDate.parse("2026-10-05"));
    assertThat(w.productNameKey()).isEqualTo(ProductNameKey.normalize("Chicken breast"));
    assertThat(w.quantity()).isEqualByComparingTo("3");
    assertThat(w.cost()).isEqualByComparingTo("12.00");
    assertThat(w.reason()).isEqualTo("Spoilage");
    assertThat(w.supplierName()).isEqualTo("Acme");
    assertThat(w.unitOfMeasurementName()).isEqualTo("kg");
    assertThat(w.unitSize()).isEqualTo("1kg");
    assertThat(w.recipeId()).isEqualTo(105);
  }

  /**
   * Real CTB response for wastage record 149, captured from the live site (HAR, 2026-10-23). CTB
   * sends {@code recordDate} as a long-form English date and {@code recipeId: -1} for plain stock
   * items; both previously made every live line fail to parse.
   */
  @Test
  void parsesRealCtbDetailResponse() throws Exception {
    byte[] json =
        Files.readAllBytes(Path.of("src/test/resources/fixtures/ctb/wastage_detail_sample.json"));

    List<WastageInput> out = parser.parse(json, RAW);

    assertThat(out).hasSize(3);
    assertThat(out)
        .allSatisfy(
            w -> {
              assertThat(w.wastageRecordId()).isEqualTo(149);
              assertThat(w.wastageDate()).isEqualTo(LocalDate.of(2026, 10, 21));
              assertThat(w.recipeId()).isNull();
              assertThat(w.rawRecordId()).isEqualTo(RAW);
            });
    assertThat(out).extracting(WastageInput::lineId).containsExactly(726, 727, 728);
    WastageInput chips = out.get(1);
    assertThat(chips.productNameKey())
        .isEqualTo(ProductNameKey.normalize("Chip Supa Crunch 10mm UF 6X2K"));
    assertThat(chips.quantity()).isEqualByComparingTo("0.5");
    assertThat(chips.cost()).isEqualByComparingTo("56.65");
    assertThat(chips.supplierName()).isEqualTo("Sealane food services");
    assertThat(chips.unitOfMeasurementName()).isEqualTo("kilograms");
    assertThat(chips.unitSize()).isEqualTo("12 kilograms");
    // cost is per stock unit: sum(quantity x cost) reproduces the header total CTB shows (154.325).
    assertThat(
            out.stream()
                .map(w -> w.quantity().multiply(w.cost()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
        .isEqualByComparingTo("154.325");
  }

  @Test
  void keepsPositiveRecipeId() {
    String json =
        """
        {"data":[{"wastageRecordId":7,"wastageRecordDetailId":1,
        "recordDate":"Monday, 5 October 2026","stockDesc":"Burger","quantity":1,"recipeId":42}]}
        """;

    assertThat(parser.parse(bytes(json), RAW).get(0).recipeId()).isEqualTo(42);
  }

  @Test
  void normalizesProductName() {
    String json =
        """
        {"data":[{"wastageRecordId":7,"wastageRecordDetailId":1,"recordDate":"2026-10-05",
        "stockDesc":" Chicken  Breast ","quantity":1}]}
        """;

    List<WastageInput> out = parser.parse(bytes(json), RAW);

    assertThat(out.get(0).productNameKey())
        .isEqualTo(ProductNameKey.normalize(" Chicken  Breast "));
  }

  @Test
  void stripsNullEscapes() {
    String nul = "\\u" + "0000";
    String json =
        "{\"data\":[{\"wastageRecordId\":7,\"wastageRecordDetailId\":1,\"recordDate\":\"2026-10-05\",\"stockDesc\":\"Oil"
            + nul
            + "\",\"quantity\":1}],\"message\":{\"IsSuccess\":true}}";

    List<WastageInput> out = parser.parse(bytes(json), RAW);

    assertThat(out.get(0).productNameKey()).doesNotContain("\u0000");
  }

  @Test
  void malformedDateThrowsSchemaMismatch() {
    String json =
        """
        {"data":[{"wastageRecordId":7,"wastageRecordDetailId":1,"recordDate":"bad",
        "stockDesc":"Oil","quantity":1}],"message":{"IsSuccess":true}}
        """;

    assertThatThrownBy(() -> parser.parse(bytes(json), RAW))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");
  }

  @Test
  void nonJsonThrowsSchemaMismatch() {
    assertThatThrownBy(() -> parser.parse(bytes("not json"), RAW))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");
  }

  @Test
  void missingWastageRecordDetailIdThrowsSchemaMismatch() {
    String json =
        """
        {"data":[{"wastageRecordId":7,"recordDate":"2026-10-05","stockDesc":"Oil","quantity":3}],
        "message":{"IsSuccess":true}}
        """;

    assertThatThrownBy(() -> parser.parse(bytes(json), RAW))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");
  }

  @Test
  void missingQuantityThrowsSchemaMismatch() {
    String json =
        """
        {"data":[{"wastageRecordId":7,"wastageRecordDetailId":359,"recordDate":"2026-10-05",
        "stockDesc":"Oil"}],"message":{"IsSuccess":true}}
        """;

    assertThatThrownBy(() -> parser.parse(bytes(json), RAW))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
