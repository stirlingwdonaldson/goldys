package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProducePackInvoicePdfExtractorTest {

  private final ProducePackInvoicePdfExtractor extractor = new ProducePackInvoicePdfExtractor();

  private static final String TEXT =
      "ORANGES & LEMONS PTY LTD\n"
          + "Invoice No\n"
          + "OL-009134\n"
          + "QTY\nCODE\nDESCRIPTION\nUOM\nPACK\n"
          + "10\nCITR001\nORANGES NAVEL\nKG\n4\n"
          + "5\nLEMON002\nLEMONS\nKG\n2\n";

  @Test
  void extractsProduceLinesWithUomAndPack() {
    PdfExtractedInvoice out = extractor.extract(TEXT);

    assertThat(out.invoiceNumber()).isEqualTo("OL-009134");
    assertThat(out.lines()).hasSize(2);
    assertThat(out.lines().get(0).stockCode()).isEqualTo("CITR001");
    assertThat(out.lines().get(0).quantity()).isEqualByComparingTo(new BigDecimal("10"));
    assertThat(out.lines().get(0).uom()).isEqualTo("KG");
    assertThat(out.lines().get(0).packSize()).isEqualByComparingTo(new BigDecimal("4"));
    assertThat(out.lines().get(1).packSize()).isEqualByComparingTo(new BigDecimal("2"));
  }

  @Test
  void returnsEmptyForNonProduceLayout() {
    assertThat(
            extractor
                .extract("QTY\nCODE\nDESCRIPTION\nUNIT\nUNIT PRICE\n1\nA\nB\nKG\n5.00\n")
                .lines())
        .isEmpty();
  }
}
