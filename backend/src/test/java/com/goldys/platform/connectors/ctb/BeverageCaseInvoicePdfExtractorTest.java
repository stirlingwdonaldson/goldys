package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BeverageCaseInvoicePdfExtractorTest {

  private final BeverageCaseInvoicePdfExtractor extractor = new BeverageCaseInvoicePdfExtractor();

  private static final String TEXT =
      "SEALANE BEVERAGES\n"
          + "Invoice #\n"
          + "SL-77120\n"
          + "QTY\nCODE\nDESCRIPTION\nUNIT PRICE\n"
          + "4 CTN / 48 EACH\nCOLA001\nCOLA CANS 375ML\n62.40\n"
          + "2 CTN / 12 EACH\nWATER002\nSPARKLING WATER 1L\n18.00\n";

  @Test
  void extractsBeverageLinesWithCaseQuantity() {
    PdfExtractedInvoice out = extractor.extract(TEXT);

    assertThat(out.invoiceNumber()).isEqualTo("SL-77120");
    assertThat(out.lines()).hasSize(2);
    PdfExtractedLine cola = out.lines().get(0);
    assertThat(cola.stockCode()).isEqualTo("COLA001");
    assertThat(cola.quantity()).isEqualByComparingTo(new BigDecimal("4"));
    assertThat(cola.uom()).isEqualTo("CTN");
    assertThat(cola.unitQuantity()).isEqualByComparingTo(new BigDecimal("48"));
    assertThat(out.lines().get(1).unitQuantity()).isEqualByComparingTo(new BigDecimal("12"));
  }

  @Test
  void returnsEmptyForNonCaseQuantityLayout() {
    assertThat(
            extractor
                .extract("QTY\nCODE\nDESCRIPTION\nUNIT\nUNIT PRICE\n1\nA\nB\nKG\n5.00\n")
                .lines())
        .isEmpty();
  }
}
