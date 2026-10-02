package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CtbClientTest {

  @Test
  void loginParsesTopLevelIsSuccess() {
    assertThat(CtbClient.isLoginSuccess("{\"IsSuccess\": true, \"AdditionalData\": {}}")).isTrue();
    assertThat(CtbClient.isLoginSuccess("{\"IsSuccess\": false, \"Info\": \"bad credentials\"}"))
        .isFalse();
  }
}
