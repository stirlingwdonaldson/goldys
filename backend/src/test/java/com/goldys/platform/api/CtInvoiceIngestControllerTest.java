package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.goldys.platform.connectors.ctb.CtInvoiceCsvIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CtInvoiceIngestControllerTest {

  private final CtInvoiceCsvIngestService csvIngest = mock(CtInvoiceCsvIngestService.class);

  @Test
  void rejectsWhenNoTokenConfigured() {
    CtInvoiceIngestController controller = new CtInvoiceIngestController(csvIngest, "");

    ResponseEntity<Void> response = controller.invoices(new byte[] {1}, "anything", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(csvIngest);
  }

  @Test
  void acceptsMatchingHeaderToken() {
    CtInvoiceIngestController controller = new CtInvoiceIngestController(csvIngest, "secret");

    ResponseEntity<Void> response = controller.invoices(new byte[] {1}, "secret", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(csvIngest).ingest(any(byte[].class));
  }

  @Test
  void rejectsWrongToken() {
    CtInvoiceIngestController controller = new CtInvoiceIngestController(csvIngest, "secret");

    ResponseEntity<Void> response = controller.invoices(new byte[] {1}, "wrong", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(csvIngest);
  }
}
