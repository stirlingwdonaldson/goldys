package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LightspeedProductCsvParserTest {

  @Test
  void parsesProductRowsAndStripsCurrency() throws Exception {
    byte[] csv =
        Files.readAllBytes(
            Path.of("src/test/resources/fixtures/lightspeed/product_sales_sample.csv"));

    var rows = new LightspeedProductCsvParser().parse(csv, LocalDate.of(2026, 9, 14));

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).productName()).isEqualTo("Pint - Carlton Draught");
    assertThat(rows.get(0).quantitySold()).isEqualByComparingTo("1232");
    assertThat(rows.get(0).amount()).isEqualByComparingTo("17340.98");
  }
}
