package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CtbClientTest {

  @Test
  void loginUsesTopLevelIsSuccess() {
    assertThat(CtbClient.isSuccess("{\"IsSuccess\": true, \"AdditionalData\": {}}")).isTrue();
    assertThat(CtbClient.isSuccess("{\"IsSuccess\": false, \"Info\": \"bad credentials\"}"))
        .isFalse();
  }

  @Test
  void dataEndpointsUseNestedMessageIsSuccess() {
    assertThat(
            CtbClient.isSuccess(
                "{\"data\": [], \"totalCount\": 0, \"message\": {\"IsSuccess\": true}}"))
        .isTrue();
    assertThat(
            CtbClient.isSuccess(
                "{\"data\": [], \"totalCount\": 0, \"message\": {\"IsSuccess\": false}}"))
        .isFalse();
  }

  @Test
  void dataEndpointsSucceedOnDataWithoutAnyIsSuccessField() {
    assertThat(CtbClient.isSuccess("{\"data\": [{\"revenueId\": 1}], \"totalCount\": 1}")).isTrue();
  }
}
