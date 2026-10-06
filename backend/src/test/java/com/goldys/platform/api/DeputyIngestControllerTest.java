package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DeputyIngestControllerTest {

  private final IngestionService ingestion = mock(IngestionService.class);

  @Test
  void rejectsWhenNoTokenConfigured() {
    DeputyIngestController controller = new DeputyIngestController(ingestion, "");

    ResponseEntity<Void> response =
        controller.deputy(new byte[] {1}, "application/json", "anything", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(ingestion);
  }

  @Test
  void acceptsMatchingHeaderToken() {
    DeputyIngestController controller = new DeputyIngestController(ingestion, "secret");

    ResponseEntity<Void> response =
        controller.deputy(new byte[] {1}, "application/json", "secret", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(ingestion)
        .ingestPush(
            eq("DEPUTY"),
            eq("deputy-webhook"),
            eq(FetchMethod.API),
            eq("application/json"),
            any(byte[].class),
            isNull(),
            eq("deputy-webhook"));
  }

  @Test
  void acceptsMatchingQueryToken() {
    DeputyIngestController controller = new DeputyIngestController(ingestion, "secret");

    ResponseEntity<Void> response =
        controller.deputy(new byte[] {1}, "application/json", null, "secret");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(ingestion)
        .ingestPush(
            eq("DEPUTY"),
            eq("deputy-webhook"),
            eq(FetchMethod.API),
            eq("application/json"),
            any(byte[].class),
            isNull(),
            eq("deputy-webhook"));
  }

  @Test
  void rejectsWrongToken() {
    DeputyIngestController controller = new DeputyIngestController(ingestion, "secret");

    ResponseEntity<Void> response =
        controller.deputy(new byte[] {1}, "application/json", "wrong", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(ingestion);
  }
}
