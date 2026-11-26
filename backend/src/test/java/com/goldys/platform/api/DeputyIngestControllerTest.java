package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DeputyIngestControllerTest {
  @Test
  void retiredWebhookRejectsEveryDelivery() {
    assertThat(new DeputyIngestController().deputy().getStatusCode()).isEqualTo(HttpStatus.GONE);
  }
}
