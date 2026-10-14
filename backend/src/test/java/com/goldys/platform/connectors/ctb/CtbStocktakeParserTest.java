package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.StockCountInput;
import com.goldys.platform.canonical.StocktakeInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbStocktakeParserTest {

  private static final UUID RAW = UUID.randomUUID();
  private final CtbStocktakeParser parser = new CtbStocktakeParser();

  @Test
  void parsesHeaderFields() {
    String json =
        """
        {"data":[{"stocktakeId":1,"stocktakeDate":"2026-10-05","stocktakeDesc":"Weekly stocktake",
        "outletName":"Goldy's","totalStockValue":1234.50,"isPartial":false,"isLockStocktake":true}],
        "message":{"IsSuccess":true},"totalCount":1}
        """;

    List<StocktakeInput> out = parser.parseStocktakes(bytes(json), RAW);

    assertThat(out).hasSize(1);
    StocktakeInput h = out.get(0);
    assertThat(h.stocktakeId()).isEqualTo(1);
    assertThat(h.stocktakeDate()).isEqualTo(LocalDate.parse("2026-10-05"));
    assertThat(h.description()).isEqualTo("Weekly stocktake");
    assertThat(h.outletName()).isEqualTo("Goldy's");
    assertThat(h.totalStockValue()).isEqualByComparingTo("1234.50");
    assertThat(h.isPartial()).isFalse();
    assertThat(h.isLocked()).isTrue();
  }

  @Test
  void malformedDateThrowsSchemaMismatch() {
    String json =
        """
        {"data":[{"stocktakeId":1,"stocktakeDate":"not-a-date"}],"message":{"IsSuccess":true}}
        """;

    assertThatThrownBy(() -> parser.parseStocktakes(bytes(json), RAW))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");
  }

  @Test
  void parsesLineFields() {
    String json =
        """
        {"data":[{"id":100,"stockId":42,"stockCode":"CHICKEN-1","stockDesc":"Chicken breast",
        "qtyCount":5,"stocktakeDetailCost":2.50,"subTotal":12.50,"supplierName":"Acme",
        "stocktakeAreaName":"Cool Room","recipeId":105}],"message":{"IsSuccess":true}}
        """;

    List<StockCountInput> out =
        parser.parseStocktakeLines(bytes(json), RAW, 1, LocalDate.parse("2026-10-05"));

    assertThat(out).hasSize(1);
    StockCountInput line = out.get(0);
    assertThat(line.stocktakeId()).isEqualTo(1);
    assertThat(line.lineId()).isEqualTo(100);
    assertThat(line.countedDate()).isEqualTo(LocalDate.parse("2026-10-05"));
    assertThat(line.productNameKey()).isEqualTo(ProductNameKey.normalize("Chicken breast"));
    assertThat(line.quantityOnHand()).isEqualByComparingTo("5");
    assertThat(line.stockId()).isEqualTo(42);
    assertThat(line.stockCode()).isEqualTo("CHICKEN-1");
    assertThat(line.unitCost()).isEqualByComparingTo("2.50");
    assertThat(line.lineValue()).isEqualByComparingTo("12.50");
    assertThat(line.supplierName()).isEqualTo("Acme");
    assertThat(line.areaName()).isEqualTo("Cool Room");
    assertThat(line.recipeId()).isEqualTo(105);
  }

  @Test
  void normalizesProductName() {
    String json =
        """
        {"data":[{"id":1,"stockDesc":" Chicken  Breast "}],"message":{"IsSuccess":true}}
        """;

    List<StockCountInput> out =
        parser.parseStocktakeLines(bytes(json), RAW, 1, LocalDate.parse("2026-10-05"));

    assertThat(out.get(0).productNameKey())
        .isEqualTo(ProductNameKey.normalize(" Chicken  Breast "));
  }

  @Test
  void nullQtyCountIsZero() {
    String json =
        """
        {"data":[{"id":1,"stockDesc":"Oil"}],"message":{"IsSuccess":true}}
        """;

    List<StockCountInput> out =
        parser.parseStocktakeLines(bytes(json), RAW, 1, LocalDate.parse("2026-10-05"));

    assertThat(out.get(0).quantityOnHand()).isEqualByComparingTo("0");
  }

  @Test
  void stripsNullEscapes() {
    String nul = "\\u" + "0000";
    String json =
        "{\"data\":[{\"id\":1,\"stockDesc\":\"Oil"
            + nul
            + "\",\"stockCode\":\"CODE"
            + nul
            + "\"}],\"message\":{\"IsSuccess\":true}}";

    List<StockCountInput> out =
        parser.parseStocktakeLines(bytes(json), RAW, 1, LocalDate.parse("2026-10-05"));

    assertThat(out.get(0).stockCode()).isEqualTo("CODE");
    assertThat(out.get(0).productNameKey()).doesNotContain("\u0000");
  }

  @Test
  void dedupesExactDuplicateLines() {
    String one = "{\"id\":100,\"stockDesc\":\"Oil\",\"qtyCount\":5}";
    String json = "{\"data\":[" + one + "," + one + "],\"message\":{\"IsSuccess\":true}}";

    List<StockCountInput> out =
        parser.parseStocktakeLines(bytes(json), RAW, 1, LocalDate.parse("2026-10-05"));

    assertThat(out).hasSize(1);
  }

  @Test
  void nonJsonThrowsSchemaMismatch() {
    assertThatThrownBy(() -> parser.parseStocktakes(bytes("not json"), RAW))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");
  }

  @Test
  void missingDataReturnsEmpty() {
    assertThat(parser.parseStocktakes(bytes("{\"message\":{\"IsSuccess\":false}}"), RAW)).isEmpty();
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
