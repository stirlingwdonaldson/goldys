package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goldys.platform.ingestion.contract.ContractValidator;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The CTB invoice CSV parser, driven by its versioned source dataset contract. */
class CtInvoiceCsvContractTest {

  private static final String CONTRACT = "connectors/ctb-invoice-csv.contract.json";

  private final CtInvoiceCsvParser parser = new CtInvoiceCsvParser();
  private final ContractValidator validator = new ContractValidator();
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private static String header() {
    return "Invoice,Supplier,SupplierCode,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax,InvoiceTotalExTax";
  }

  private static byte[] csv(String... rows) {
    return (header() + "\n" + String.join("\n", rows)).getBytes(StandardCharsets.UTF_8);
  }

  // 1. Valid expected input parses and conforms to the JSON Schema.
  @Test
  void validInputParsesAndConforms() {
    ParseResult result =
        parser.parse(csv("INV-1,Bruno's,BRUNO,2026-09-20,STK-7,Beer,1 EACH,120.00,120.00"));

    assertThat(result.invoices()).hasSize(1);
    assertThat(result.unknownColumns()).isEmpty();
    assertThat(validator.validateOutput(CONTRACT, toJson(result.invoices().get(0)))).isEmpty();
  }

  // 2. Missing required columns fail with a diagnosable schema-mismatch error.
  @Test
  void missingRequiredColumnFails() {
    byte[] csv =
        "Invoice,Supplier,Date\nINV-1,Bruno's,2026-09-20\n".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasFieldOrPropertyWithValue("failureType", "CONNECTOR_SCHEMA_MISMATCH")
        .hasMessageContaining("Missing required column")
        .hasMessageContaining("StockCode");
  }

  // 3. A previously unknown column is additive drift — surfaced, not silently discarded.
  @Test
  void unknownColumnSurfacesAsDrift() {
    byte[] csv =
        (header()
                + ",BrandNewColumn\nINV-1,Bruno's,BRUNO,2026-09-20,STK-7,Beer,1 EACH,120.00,120.00,x\n")
            .getBytes(StandardCharsets.UTF_8);

    ParseResult result = parser.parse(csv);
    assertThat(result.invoices()).hasSize(1);
    assertThat(result.unknownColumns()).contains("BrandNewColumn");
  }

  // 4a. A wrong numeric type fails.
  @Test
  void wrongNumericTypeFails() {
    byte[] csv =
        "Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
            .concat("INV-1,Bruno's,2026-09-20,STK-7,Beer,1 EACH,not-a-number\n")
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Bad amount");
  }

  // 4b. A wrong date type fails.
  @Test
  void wrongDateTypeFails() {
    byte[] csv =
        "Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
            .concat("INV-1,Bruno's,not-a-date,STK-7,Beer,1 EACH,120.00\n")
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Bad date");
  }

  // 5. A header-only delivery is a legitimate empty response, not a failure.
  @Test
  void headerOnlyDeliveryIsEmptyNotFailure() {
    ParseResult result = parser.parse(header().getBytes(StandardCharsets.UTF_8));
    assertThat(result.invoices()).isEmpty();
  }

  // 6. Duplicate source records parse idempotently (same normalized output).
  @Test
  void duplicateDeliveryIsIdempotent() {
    byte[] csv = csv("INV-1,Bruno's,BRUNO,2026-09-20,STK-7,Beer,1 EACH,120.00,120.00");
    List<CtInvoice> first = parser.parse(csv).invoices();
    List<CtInvoice> second = parser.parse(csv).invoices();
    assertThat(first).hasSize(1);
    assertThat(second.get(0).invoiceNumber()).isEqualTo(first.get(0).invoiceNumber());
    assertThat(second.get(0).lines()).hasSize(first.get(0).lines().size());
  }

  // 7. A changed source schema (a required column renamed) fails loudly.
  @Test
  void changedSourceSchemaFails() {
    byte[] csv =
        "InvoiceNo,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
            .concat("INV-1,Bruno's,2026-09-20,STK-7,Beer,1 EACH,120.00\n")
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasFieldOrPropertyWithValue("failureType", "CONNECTOR_SCHEMA_MISMATCH")
        .hasMessageContaining("Missing required column");
  }

  // 8. An invalid source identifier (blank invoice number) fails.
  @Test
  void blankInvoiceNumberFails() {
    byte[] csv =
        "Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
            .concat(",Bruno's,2026-09-20,STK-7,Beer,1 EACH,120.00\n")
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Missing value for 'Invoice'");
  }

  // 9. Unknown versus zero: a blank unit cost stays null; an explicit zero stays zero.
  @Test
  void unknownStaysNullAndZeroStaysZero() {
    ParseResult result =
        parser.parse(
            csv(
                "INV-1,Bruno's,BRUNO,2026-09-20,STK-7,Beer,1 EACH,120.00,120.00",
                "INV-2,Bruno's,BRUNO,2026-09-20,STK-8,Freebie,1 EACH,0.00,0.00"));
    CtInvoice invoice =
        result.invoices().stream()
            .filter(i -> i.invoiceNumber().equals("INV-1"))
            .findFirst()
            .orElseThrow();
    CtInvoiceLine line = invoice.lines().get(0);
    assertThat(line.lineTotalExTax()).isEqualByComparingTo("120.00");
    // unitCostExTax is optional; a blank column parses to null (never fabricated as zero).
    assertThat(line.unitCostExTax()).isNull();

    CtInvoice zero =
        result.invoices().stream()
            .filter(i -> i.invoiceNumber().equals("INV-2"))
            .findFirst()
            .orElseThrow();
    assertThat(zero.lines().get(0).lineTotalExTax()).isEqualByComparingTo("0.00");
  }

  // 10. Historical contract compatibility: the current parser output conforms to the pinned schema.
  @Test
  void outputConformsToPinnedContract() {
    ParseResult result =
        parser.parse(csv("INV-1,Bruno's,BRUNO,2026-09-20,STK-7,Beer,1 EACH,120.00,120.00"));
    JsonNode json = toJson(result.invoices().get(0));
    List<String> violations = validator.validateOutput(CONTRACT, json);
    assertThat(violations).isEmpty();
  }

  private JsonNode toJson(CtInvoice invoice) {
    return mapper.valueToTree(invoice);
  }
}
