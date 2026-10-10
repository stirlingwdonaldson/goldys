package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LightspeedSaleItemCsvParserTest {

  @Test
  void parsesRowsByHeaderName() throws Exception {
    byte[] csv =
        Files.readAllBytes(Path.of("src/test/resources/fixtures/lightspeed/sale_items_sample.csv"));

    List<LightspeedSaleItem> items = new LightspeedSaleItemCsvParser().parse(csv);

    assertThat(items).hasSize(3);
    LightspeedSaleItem first = items.get(0);
    assertThat(first.receiptLineId()).isEqualTo("LI-1");
    assertThat(first.saleDate()).isEqualTo(LocalDate.parse("2026-09-19"));
    assertThat(first.itemName()).isEqualTo("Burger");
    assertThat(first.quantity()).isEqualTo(1);
    assertThat(first.amount()).isEqualByComparingTo("22.00");
    assertThat(first.soldPriceIncTax()).isEqualByComparingTo("22.00");
    assertThat(first.totalTax()).isEqualByComparingTo("2.00");
    assertThat(first.registerName()).isEqualTo("Goldy's Main Bar");

    // Nullable fields are preserved or nulled correctly.
    LightspeedSaleItem third = items.get(2);
    assertThat(third.sku()).isNull();
    assertThat(third.costIncTax()).isNull();
    assertThat(third.saleType()).isEqualTo("Refund");
  }

  @Test
  void failsOnMissingRequiredColumn() {
    String csv = "Sales Data Receipt Line ID,Sales Data Sale Type\n" + "LI-1,Sale\n";

    assertThatThrownBy(
            () -> new LightspeedSaleItemCsvParser().parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Sales Data Sale Closed Date");
  }
}
