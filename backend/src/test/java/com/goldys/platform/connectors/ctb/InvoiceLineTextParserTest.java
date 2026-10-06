package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceLineTextParserTest {

  private final InvoiceLineTextParser parser = new InvoiceLineTextParser();

  @Test
  void parsesLineItemsAndSkipsHeaderNoise() {
    String text =
        "Item          Qty    Unit     Total\n"
            + "Potatoes      2      12.50    25.00\n"
            + "Beef          1      45.00    45.00\n";

    List<InvoiceLine> lines = parser.parse(text);

    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).productNameKey()).isEqualTo("potatoes");
    assertThat(lines.get(0).quantity()).isEqualByComparingTo(new BigDecimal("2"));
    assertThat(lines.get(0).unitCost()).isEqualByComparingTo(new BigDecimal("12.50"));
    assertThat(lines.get(0).lineTotal()).isEqualByComparingTo(new BigDecimal("25.00"));
    assertThat(lines.get(1).productNameKey()).isEqualTo("beef");
  }

  @Test
  void noRecognizableLinesYieldsEmptyList() {
    assertThat(parser.parse("Invoice\nSupplier: Bidfood\nTotal: $1200.00\n")).isEmpty();
  }
}
