package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.WastageInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
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

  @Test
  void normalizesProductName() {
    String json =
        """
        {"data":[{"wastageRecordId":7,"wastageRecordDetailId":1,"recordDate":"2026-10-05",
        "stockDesc":" Chicken  Breast "}]}
        """;

    List<WastageInput> out = parser.parse(bytes(json), RAW);

    assertThat(out.get(0).productNameKey()).isEqualTo(ProductNameKey.normalize(" Chicken  Breast "));
  }

  @Test
  void stripsNullEscapes() {
    String nul = "\\u" + "0000";
    String json =
        "{\"data\":[{\"wastageRecordId\":7,\"wastageRecordDetailId\":1,\"recordDate\":\"2026-10-05\",\"stockDesc\":\"Oil"
            + nul
            + "\"}],\"message\":{\"IsSuccess\":true}}";

    List<WastageInput> out = parser.parse(bytes(json), RAW);

    assertThat(out.get(0).productNameKey()).doesNotContain("\u0000");
  }

  @Test
  void malformedDateThrowsSchemaMismatch() {
    String json =
        """
        {"data":[{"wastageRecordDetailId":1,"recordDate":"bad"}],"message":{"IsSuccess":true}}
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

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
