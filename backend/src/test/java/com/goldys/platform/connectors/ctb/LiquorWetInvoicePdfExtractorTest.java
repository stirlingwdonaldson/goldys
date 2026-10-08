package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LiquorWetInvoicePdfExtractorTest {

  private final LiquorWetInvoicePdfExtractor extractor = new LiquorWetInvoicePdfExtractor();

  private static final String TEXT =
      "PARAMOUNT LIQUOR PTY LTD\n"
          + "Invoice Number\n"
          + "PL-004821\n"
          + "QTY\nCODE\nDESCRIPTION\nUNIT\nUNIT PRICE\nWET\n"
          + "12\nGOLD306600\nGOLDY'S HOUSE RED 1L\nEACH\n21.50\n18.50\n"
          + "6\nSPIRIT001\nGIN 700ML\nEACH\n38.00\n12.75\n"
          + "Total\n476.58\n";

  @Test
  void extractsLiquorLinesWithWet() {
    PdfExtractedInvoice out = extractor.extract(TEXT);

    assertThat(out.invoiceNumber()).isEqualTo("PL-004821");
    assertThat(out.lines()).hasSize(2);
    assertThat(out.lines().get(0).stockCode()).isEqualTo("GOLD306600");
    assertThat(out.lines().get(0).description()).isEqualTo("GOLDY'S HOUSE RED 1L");
    assertThat(out.lines().get(0).quantity()).isEqualByComparingTo(new BigDecimal("12"));
    assertThat(out.lines().get(0).uom()).isEqualTo("EACH");
    assertThat(out.lines().get(0).wetAmount()).isEqualByComparingTo(new BigDecimal("18.50"));
    assertThat(out.lines().get(1).wetAmount()).isEqualByComparingTo(new BigDecimal("12.75"));
  }

  @Test
  void returnsEmptyForNonLiquorLayout() {
    assertThat(
            extractor
                .extract("QTY\nCODE\nDESCRIPTION\nUNIT\nUNIT PRICE\n1\nA\nB\nKG\n5.00\n")
                .lines())
        .isEmpty();
  }
}
