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

  @Test
  void permissionDeniedDetectsTheAuthorityHtmlInAJsonEnvelope() {
    // A denied action is still JSON, but Info (and message.Info) carries the HTML denial string.
    String body =
        "{\"IsSuccess\":false,\"Info\":\"<p><b>You don't have authority to perform this action.</b></p>\","
            + "\"message\":{\"IsSuccess\":false,\"Info\":\"<p><b>You don't have authority to perform this action.</b></p>\"}}";
    assertThat(CtbClient.isPermissionDenied(body)).isTrue();
  }

  @Test
  void permissionDeniedDetectsABareHtmlDenialPage() {
    assertThat(
            CtbClient.isPermissionDenied(
                "<html><body><p><b>You don't have authority to perform this action.</b></p></body></html>"))
        .isTrue();
  }

  @Test
  void permissionDeniedIsFalseForNormalBodies() {
    assertThat(CtbClient.isPermissionDenied("{\"data\": [{\"revenueId\": 1}], \"totalCount\": 1}"))
        .isFalse();
    assertThat(CtbClient.isPermissionDenied(null)).isFalse();
    assertThat(CtbClient.isPermissionDenied("")).isFalse();
  }
}
