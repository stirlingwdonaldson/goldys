package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.goldys.platform.connectors.ctb.CtInvoiceCsvIngestService;
import com.goldys.platform.connectors.ctb.CtInvoicePdfIngestService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CtInvoiceIngestControllerTest {

  private final CtInvoiceCsvIngestService csvIngest = mock(CtInvoiceCsvIngestService.class);
  private final CtInvoicePdfIngestService pdfIngest = mock(CtInvoicePdfIngestService.class);

  @Test
  void rejectsWhenNoTokenConfigured() {
    CtInvoiceIngestController controller =
        new CtInvoiceIngestController(csvIngest, pdfIngest, "");

    ResponseEntity<Void> response = controller.invoices(new byte[] {1}, "anything", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(csvIngest);
  }

  @Test
  void acceptsMatchingHeaderToken() {
    CtInvoiceIngestController controller =
        new CtInvoiceIngestController(csvIngest, pdfIngest, "secret");

    ResponseEntity<Void> response = controller.invoices(new byte[] {1}, "secret", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(csvIngest).ingest(any(byte[].class));
  }

  @Test
  void rejectsWrongToken() {
    CtInvoiceIngestController controller =
        new CtInvoiceIngestController(csvIngest, pdfIngest, "secret");

    ResponseEntity<Void> response = controller.invoices(new byte[] {1}, "wrong", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(csvIngest);
  }

  @Test
  void pdfEndpointIngestsWithInvoiceAttribution() {
    CtInvoiceIngestController controller =
        new CtInvoiceIngestController(csvIngest, pdfIngest, "secret");

    ResponseEntity<Void> response =
        controller.invoicePdf(new byte[] {1}, "INV-1001", LocalDate.of(2026, 9, 20), "secret", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(pdfIngest).ingest(any(byte[].class), eq("INV-1001"), eq(LocalDate.of(2026, 9, 20)));
  }
}
