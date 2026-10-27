package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CtInvoiceCsvParserTest {

  private final CtInvoiceCsvParser parser = new CtInvoiceCsvParser();

  private static final String HEADER =
      "OutletName,SupplierGLCode,InvoiceDueDate,InvoiceTotalExTax,InvoiceFreight,InvoiceFreightExTax,"
          + "SupplierCode,Supplier,Date,Invoice,Total,GST,PONumber,PDF,"
          + "StockCode,StockDescription,LineQuantity,LineUnitCostExTax,LineTotalExTax,LineTaxFlag\n";

  @Test
  void retainsObservedInvoiceSemanticsWithoutManufacturingBalancedAmounts() throws Exception {
    var invoices =
        new CtInvoiceCsvParser()
            .parse(
                java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of(
                        "src/test/resources/fixtures/ctb/invoice-semantics-evidence.csv")))
            .invoices();
    assertThat(invoices).hasSize(7);
    var bridge =
        invoices.stream()
            .filter(v -> v.invoiceNumber().equals("INV8365286"))
            .findFirst()
            .orElseThrow();
    var line = bridge.lines().getFirst();
    assertThat(line.lineTotalExTax()).isEqualByComparingTo("459.77");
    assertThat(line.lineDiscount()).isEqualByComparingTo("59.77");
    assertThat(line.lineTotalIncTax()).isEqualByComparingTo("440");
    assertThat(line.hasUnverifiedAmountBasis()).isTrue();
    var credit =
        invoices.stream()
            .filter(v -> v.invoiceNumber().equals("5674251"))
            .findFirst()
            .orElseThrow();
    assertThat(credit.documentType()).isEqualTo("Return");
    assertThat(credit.lines().getFirst().rawQuantity()).isEqualTo("-1");
    assertThat(credit.lines().getFirst().lineTax()).isEqualByComparingTo("-20.03");
    assertThat(credit.lines().getFirst().lineDiscount()).isZero();
    assertThat(credit.lines().getFirst().hasUnverifiedAmountBasis()).isFalse();
    assertThat(credit.lines().get(1).lineTotalExTax()).isEqualByComparingTo("0.24");
    var astro =
        invoices.stream()
            .filter(v -> v.invoiceNumber().equals("00003794"))
            .findFirst()
            .orElseThrow();
    assertThat(astro.invoiceNumber()).startsWith("0000");
    assertThat(astro.freightAmountExTax()).isEqualByComparingTo("11");
    var principal =
        invoices.stream()
            .filter(v -> v.invoiceNumber().equals("INV-57074"))
            .findFirst()
            .orElseThrow();
    assertThat(principal.lines().get(1).stockCode()).isEqualTo("LHPN241275");
    assertThat(principal.lines().get(1).lineDiscount()).isEqualByComparingTo("44.55");
  }

  @Test
  void absentContextIsUnknownRatherThanZeroAndPrecisionIsUnchanged() {
    var invoice =
        new CtInvoiceCsvParser()
            .parse(
                ("Invoice,Supplier,Date,StockCode,StockDescription,LineTotalExTax,LineTax,LineDiscount\n"
                        + "X,Test,2026-10-27,C,Item,1.123456,,0.000000\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .invoices()
            .getFirst();
    var line = invoice.lines().getFirst();
    assertThat(line.lineTotalExTax().toPlainString()).isEqualTo("1.123456");
    assertThat(line.lineTax()).isNull();
    assertThat(line.lineDiscount().toPlainString()).isEqualTo("0.000000");
    assertThat(invoice.freightAmountExTax()).isNull();
  }

  @Test
  void parsesHeaderAndLines() {
    byte[] csv =
        (HEADER
                + "Goldys,GL-1,2026-10-10,90.91,25.00,22.73,GOLD306600,Bruno's,2026-09-20,INV-2001,117.64,9.09,PO-1,bruno-1956.pdf,"
                + "STK-7,Bruno Premium Lager,\"4 CTN / 48 EACH\",2.50,120.00,true\n"
                + "Goldys,GL-1,2026-10-10,90.91,25.00,22.73,GOLD306600,Bruno's,2026-09-20,INV-2001,117.64,9.09,PO-1,bruno-1956.pdf,"
                + "STK-8,Bruno Pale Ale,1 EACH,3.00,3.00,true\n")
            .getBytes(StandardCharsets.UTF_8);

    List<CtInvoice> invoices = parser.parse(csv).invoices();

    assertThat(invoices).hasSize(1);
    CtInvoice invoice = invoices.get(0);
    assertThat(invoice.invoiceNumber()).isEqualTo("INV-2001");
    assertThat(invoice.supplierName()).isEqualTo("Bruno's");
    assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    assertThat(invoice.dueDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    assertThat(invoice.purchaseNumber()).isEqualTo("PO-1");
    assertThat(invoice.amountExTax()).isEqualByComparingTo(new BigDecimal("90.91"));
    assertThat(invoice.gstAmount()).isEqualByComparingTo(new BigDecimal("9.09"));
    assertThat(invoice.incTaxAmount()).isEqualByComparingTo(new BigDecimal("117.64"));
    assertThat(invoice.pdfFilenames()).containsExactly("bruno-1956.pdf");
    assertThat(invoice.lines()).hasSize(2);
    assertThat(invoice.lines().get(0).stockCode()).isEqualTo("STK-7");
    assertThat(invoice.lines().get(0).lineTotalExTax())
        .isEqualByComparingTo(new BigDecimal("120.00"));
  }

  @Test
  void groupsLinesByInvoiceNumber() {
    byte[] csv =
        (HEADER
                + "Goldys,GL-1,,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,bruno-1.pdf,STK-7,Beer,1 EACH,3.00,3.00,true\n"
                + "Goldys,GL-2,,,,,GOLD306600,Bruno's,2026-09-21,INV-2002,,,,bruno-2.pdf,STK-8,Chips,2 EACH,1.50,3.00,true\n")
            .getBytes(StandardCharsets.UTF_8);

    List<CtInvoice> invoices = parser.parse(csv).invoices();

    assertThat(invoices).hasSize(2);
    assertThat(invoices.get(0).invoiceNumber()).isEqualTo("INV-2001");
    assertThat(invoices.get(0).invoiceDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    assertThat(invoices.get(0).lines()).hasSize(1);
    assertThat(invoices.get(0).lines().get(0).stockCode()).isEqualTo("STK-7");
    assertThat(invoices.get(1).invoiceNumber()).isEqualTo("INV-2002");
    assertThat(invoices.get(1).invoiceDate()).isEqualTo(LocalDate.of(2026, 9, 21));
    assertThat(invoices.get(1).lines()).hasSize(1);
    assertThat(invoices.get(1).lines().get(0).stockCode()).isEqualTo("STK-8");
  }

  @Test
  void parsesCtbDayMonthYearDates() {
    byte[] csv =
        (HEADER
                + "Goldys,GL-1,9/05/2026,90.91,25.00,22.73,GOLD306600,Bruno's,1/05/2026,346489,117.64,9.09,PO-1,bruno-1.pdf,STK-7,Beer,1,3.00,3.00,true\n")
            .getBytes(StandardCharsets.UTF_8);

    CtInvoice invoice = parser.parse(csv).invoices().get(0);

    assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    assertThat(invoice.dueDate()).isEqualTo(LocalDate.of(2026, 5, 9));
  }

  @Test
  void skipsBlankLineRows() {
    byte[] csv =
        (HEADER
                + "Goldys,GL-1,,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,bruno-1956.pdf,,,,,,\n"
                + "Goldys,GL-1,,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,bruno-1956.pdf,STK-7,Beer,1 EACH,3.00,3.00,true\n")
            .getBytes(StandardCharsets.UTF_8);

    List<CtInvoice> invoices = parser.parse(csv).invoices();

    assertThat(invoices).hasSize(1);
    assertThat(invoices.get(0).lines()).hasSize(1);
  }

  @Test
  void collectsDistinctPdfFilenamesPerInvoice() {
    byte[] csv =
        (HEADER
                + "Goldys,GL-1,,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,bruno-1.pdf,STK-7,Beer,1 EACH,3.00,3.00,true\n"
                + "Goldys,GL-1,,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,bruno-2.pdf,STK-8,Chips,2 EACH,1.50,3.00,true\n")
            .getBytes(StandardCharsets.UTF_8);

    CtInvoice invoice = parser.parse(csv).invoices().get(0);

    assertThat(invoice.pdfFilenames()).containsExactly("bruno-1.pdf", "bruno-2.pdf");
  }

  @Test
  void leavesPdfFilenamesEmptyWhenTheColumnIsBlank() {
    byte[] csv =
        (HEADER
                + "Goldys,GL-1,,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,,STK-7,Beer,1 EACH,3.00,3.00,true\n")
            .getBytes(StandardCharsets.UTF_8);

    CtInvoice invoice = parser.parse(csv).invoices().get(0);

    assertThat(invoice.pdfFilenames()).isEmpty();
  }

  @Test
  void rejectsAMissingColumn() {
    byte[] csv =
        "Invoice,Supplier,Date\nINV-1,Bruno's,2026-09-20\n".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Missing required column");
  }

  @Test
  void duplicateNumbersRemainSeparateAcrossKnownIssuers() {
    String csv =
        "Invoice,SupplierCode,Supplier,Date,StockCode,StockDescription,LineTotalExTax\n"
            + "42,A,Alpha,1/10/2026,O,Oil,100\n42,B,Beta,2/10/2026,F,Flour,200\n";
    assertThat(
            new CtInvoiceCsvParser()
                .parse(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .invoices())
        .hasSize(2);
  }

  @Test
  void oneIssuerNumberAcrossOutletsRequiresIdentityReview() {
    String csv =
        "Invoice,SupplierCode,Supplier,Date,StockCode,StockDescription,LineTotalExTax,OutletCode\n"
            + "42,A,Alpha,1/10/2026,O,Oil,100,ONE\n42,A,Alpha,1/10/2026,F,Flour,200,TWO\n";
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new CtInvoiceCsvParser()
                    .parse(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .isInstanceOf(com.goldys.platform.ingestion.port.ConnectorFetchException.class)
        .hasMessageContaining("outlet/document");
  }
}
