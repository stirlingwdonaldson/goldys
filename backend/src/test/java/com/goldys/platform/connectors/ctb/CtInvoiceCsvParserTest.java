package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CtInvoiceCsvParserTest {

  private final CtInvoiceCsvParser parser = new CtInvoiceCsvParser();

  private static final String HEADER =
      "OutletName,SupplierGLCode,InvoiceDueDate,InvoiceTotalExTax,InvoiceFreight,InvoiceFreightExTax,"
          + "SupplierCode,Supplier,Date,Invoice,Total,GST,PONumber,PDF,"
          + "StockCode,StockDescription,LineQuantity,LineUnitCostExTax,LineTotalExTax,LineTaxFlag\n";

  @Test
  void parsesHeaderAndLines() {
    byte[] csv =
        (HEADER
                + "Goldys,GL-1,2026-10-10,90.91,25.00,22.73,GOLD306600,Bruno's,2026-09-20,INV-2001,117.64,9.09,PO-1,bruno-1956.pdf,"
                + "STK-7,Bruno Premium Lager,\"4 CTN / 48 EACH\",2.50,120.00,true\n"
                + "Goldys,GL-1,2026-10-10,90.91,25.00,22.73,GOLD306600,Bruno's,2026-09-20,INV-2001,117.64,9.09,PO-1,bruno-1956.pdf,"
                + "STK-8,Bruno Pale Ale,1 EACH,3.00,3.00,true\n")
            .getBytes(StandardCharsets.UTF_8);

    CtInvoice invoice = parser.parse(csv);

    assertThat(invoice.invoiceNumber()).isEqualTo("INV-2001");
    assertThat(invoice.supplierName()).isEqualTo("Bruno's");
    assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    assertThat(invoice.dueDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    assertThat(invoice.purchaseNumber()).isEqualTo("PO-1");
    assertThat(invoice.amountExTax()).isEqualByComparingTo(new BigDecimal("90.91"));
    assertThat(invoice.gstAmount()).isEqualByComparingTo(new BigDecimal("9.09"));
    assertThat(invoice.incTaxAmount()).isEqualByComparingTo(new BigDecimal("117.64"));
    assertThat(invoice.lines()).hasSize(2);
    assertThat(invoice.lines().get(0).stockCode()).isEqualTo("STK-7");
    assertThat(invoice.lines().get(0).lineTotalExTax())
        .isEqualByComparingTo(new BigDecimal("120.00"));
  }

  @Test
  void skipsBlankLineRows() {
    byte[] csv =
        (HEADER
                + "Goldys,GL-1,,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,bruno-1956.pdf,,,,,,\n"
                + "Goldys,GL-1,,,,,GOLD306600,Bruno's,2026-09-20,INV-2001,,,,bruno-1956.pdf,STK-7,Beer,1 EACH,3.00,3.00,true\n")
            .getBytes(StandardCharsets.UTF_8);

    assertThat(parser.parse(csv).lines()).hasSize(1);
  }

  @Test
  void rejectsAMissingColumn() {
    byte[] csv =
        "Invoice,Supplier,Date\nINV-1,Bruno's,2026-09-20\n".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Missing column");
  }
}
