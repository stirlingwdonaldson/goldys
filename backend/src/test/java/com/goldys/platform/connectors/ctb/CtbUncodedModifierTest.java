package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbUncodedModifierTest {
  @Test
  void uncodedModifierRetainsCompletenessWithoutInventingProductIdentity() throws Exception {
    var items =
        new CtbSaleItemParser()
            .parse(
                java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of(
                        "src/test/resources/fixtures/ctb/sale-items-206-page1.json")));
    var batch = new CtbProductSalesBatch(LocalDate.of(2026, 9, 5));
    UUID raw = UUID.randomUUID();
    items.forEach(i -> batch.add(i, raw));
    items.forEach(i -> batch.add(i, raw));
    assertThat(batch.rowCount()).isEqualTo(200);
    assertThat(batch.sourceFacts())
        .hasSize(199)
        .allSatisfy(f -> assertThat(f.stockCode()).isNotBlank());
    assertThat(batch.totals()).hasSize(199);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "amount",
        "costPerUnit",
        "totalFoodCost",
        "foodCost",
        "discountedAmount",
        "taxAmount",
        "unitPrice",
        "parentStockCode",
        "uniqueId",
        "quantitySold",
        "stockDescription"
      })
  void unidentifiedRowsWithValueOrMissingModifierEvidenceStillRejectTheDay(String field)
      throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var root =
        mapper.readTree(
            java.nio.file.Path.of("src/test/resources/fixtures/ctb/sale-items-206-page1.json")
                .toFile());
    var modifier = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("data").get(6);
    if (java.util.Set.of("parentStockCode", "uniqueId", "quantitySold", "stockDescription")
        .contains(field)) modifier.putNull(field);
    else modifier.put(field, 1);
    var batch = new CtbProductSalesBatch(LocalDate.of(2026, 9, 5));
    var items = new CtbSaleItemParser().parse(mapper.writeValueAsBytes(root));
    assertThatThrownBy(() -> items.forEach(i -> batch.add(i, UUID.randomUUID())))
        .hasMessageContaining("identity or quantity/amount");
  }
}
