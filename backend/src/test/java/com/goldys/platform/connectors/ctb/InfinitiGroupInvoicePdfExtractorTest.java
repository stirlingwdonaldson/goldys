package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InfinitiGroupInvoicePdfExtractorTest {

  private static final String TEXT =
      """
      Product Code  Product Description Unit QTY Sup B/O Each Ex Total Ex Tax Total Inc
      B12BG Brush Grill Medium Prince Castle each/1 1 1 0 $175.00 $175.00 $17.50 $192.50
      Total Ex GST: $175.00
      """;

  @Test
  void parsesTheLineAndItsExTaxTotal() {
    PdfExtractedInvoice inv = new InfinitiGroupInvoicePdfExtractor().extract(TEXT);
    assertThat(inv.lines()).hasSize(1);
    PdfExtractedLine l = inv.lines().get(0);
    assertThat(l.stockCode()).isEqualTo("B12BG");
    assertThat(l.description()).isEqualTo("Brush Grill Medium Prince Castle");
    assertThat(l.amountExTax()).isEqualByComparingTo("175.00");
  }

  @Test
  void keepsTheGstAndTaxInclusiveColumnsInsteadOfDroppingThem() {
    PdfExtractedInvoice inv = new InfinitiGroupInvoicePdfExtractor().extract(TEXT);
    PdfExtractedLine l = inv.lines().get(0);
    assertThat(l.gstAmount()).isEqualByComparingTo("17.50");
    assertThat(l.amountIncTax()).isEqualByComparingTo("192.50");
    assertThat(l.incGst()).isTrue();
    assertThat(l.taxRate()).isNull();
  }

  @Test
  void returnsEmptyOnAnotherLayout() {
    assertThat(new InfinitiGroupInvoicePdfExtractor().extract("other").lines()).isEmpty();
  }
}
