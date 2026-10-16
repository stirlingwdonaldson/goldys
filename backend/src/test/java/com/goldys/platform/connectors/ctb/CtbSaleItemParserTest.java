package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CtbSaleItemParserTest {

  private final CtbSaleItemParser parser = new CtbSaleItemParser();

  @Test
  void parsesRows() throws Exception {
    byte[] json =
        Files.readAllBytes(Path.of("src/test/resources/fixtures/ctb/sale_items_sample.json"));

    var rows = new CtbSaleItemParser().parse(json);

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).stockDescription()).isEqualTo("(0.5L) Goldy's Pinot Grigio");
    assertThat(rows.get(0).quantitySold()).isEqualByComparingTo("3");
    assertThat(rows.get(1).amount()).isEqualByComparingTo("2732.07");
  }

  @Test
  void parsesSourceNativeMeasuresAndGroupingKey() {
    String json =
        "{\"data\":[{\"stockCode\":\"STK-1\",\"stockDescription\":\"Beer\",\"quantitySold\":3,"
            + "\"amount\":120.66,\"costPerUnit\":12.50,\"foodCost\":11.00,\"totalFoodCost\":250.00,"
            + "\"profit\":48.00,\"discountedAmount\":10.00,\"taxAmount\":22.73,\"unitPrice\":13.75,"
            + "\"uniqueGroupId\":\"G-1\"}]}";
    CtbSaleItem item = parser.parse(json.getBytes(StandardCharsets.UTF_8)).get(0);
    assertThat(item.stockCode()).isEqualTo("STK-1");
    assertThat(item.costPerUnit()).isEqualByComparingTo("12.50");
    assertThat(item.foodCost()).isEqualByComparingTo("11.00");
    assertThat(item.totalFoodCost()).isEqualByComparingTo("250.00");
    assertThat(item.profit()).isEqualByComparingTo("48.00");
    assertThat(item.discountedAmount()).isEqualByComparingTo("10.00");
    assertThat(item.taxAmount()).isEqualByComparingTo("22.73");
    assertThat(item.unitPrice()).isEqualByComparingTo("13.75");
    assertThat(item.uniqueGroupId()).isEqualTo("G-1");
  }

  @Test
  void preservesNullsAndZerosDistinctly() {
    String json =
        "{\"data\":[{\"stockCode\":\"STK-1\",\"stockDescription\":\"Beer\",\"quantitySold\":0,"
            + "\"amount\":0,\"costPerUnit\":null,\"foodCost\":null,\"totalFoodCost\":0,"
            + "\"profit\":null,\"discountedAmount\":0,\"taxAmount\":0,\"unitPrice\":null,"
            + "\"uniqueGroupId\":null}]}";
    CtbSaleItem item = parser.parse(json.getBytes(StandardCharsets.UTF_8)).get(0);
    // Missing values stay null — never fabricated as zero.
    assertThat(item.costPerUnit()).isNull();
    assertThat(item.foodCost()).isNull();
    assertThat(item.profit()).isNull();
    assertThat(item.unitPrice()).isNull();
    assertThat(item.uniqueGroupId()).isNull();
    // Present zeros stay zero — never conflated with null.
    assertThat(item.totalFoodCost()).isEqualByComparingTo("0");
    assertThat(item.discountedAmount()).isEqualByComparingTo("0");
    assertThat(item.taxAmount()).isEqualByComparingTo("0");
  }

  @Test
  void distinguishesSaleItemEnvelopeFromRevenueAndNonJson() {
    String saleItems = "{\"data\":[{\"stockCode\":\"STK-1\",\"stockDescription\":\"Beer\"}]}";
    String revenue = "{\"data\":[{\"revenueId\":1,\"totalSales\":100.0}]}";
    assertThat(parser.isSaleItemEnvelope(saleItems.getBytes(StandardCharsets.UTF_8))).isTrue();
    assertThat(parser.isSaleItemEnvelope(revenue.getBytes(StandardCharsets.UTF_8))).isFalse();
    assertThat(parser.isSaleItemEnvelope("not json".getBytes(StandardCharsets.UTF_8))).isFalse();
  }
}
