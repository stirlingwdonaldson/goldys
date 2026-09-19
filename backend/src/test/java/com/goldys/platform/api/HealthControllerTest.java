package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
  @Test
  void reportsTheApplicationAsUp() {
    assertThat(new HealthController().health().status()).isEqualTo("UP");
  }
}
