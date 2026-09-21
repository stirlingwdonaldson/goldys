package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CtbSaleItemParserTest {

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
}
