package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.goldys.platform.connectors.opentable.OpenTableCsvIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OpenTableCsvIngestControllerTest {

  private final OpenTableCsvIngestService ingestService = mock(OpenTableCsvIngestService.class);

  @Test
  void rejectsWhenNoTokenConfigured() {
    OpenTableCsvIngestController controller = new OpenTableCsvIngestController(ingestService, "");

    ResponseEntity<Void> response = controller.opentable(new byte[] {1}, "anything", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(ingestService);
  }

  @Test
  void acceptsMatchingHeaderToken() {
    OpenTableCsvIngestController controller =
        new OpenTableCsvIngestController(ingestService, "secret");

    ResponseEntity<Void> response = controller.opentable(new byte[] {1}, "secret", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(ingestService).ingest(any(byte[].class));
  }

  @Test
  void acceptsMatchingQueryToken() {
    OpenTableCsvIngestController controller =
        new OpenTableCsvIngestController(ingestService, "secret");

    ResponseEntity<Void> response = controller.opentable(new byte[] {1}, null, "secret");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(ingestService).ingest(any(byte[].class));
  }

  @Test
  void rejectsWrongToken() {
    OpenTableCsvIngestController controller =
        new OpenTableCsvIngestController(ingestService, "secret");

    ResponseEntity<Void> response = controller.opentable(new byte[] {1}, "wrong", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(ingestService);
  }
}
