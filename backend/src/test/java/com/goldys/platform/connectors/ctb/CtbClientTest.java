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

  @Test
  void nonJsonDetailCarriesTheStatusTypeAndABoundedPrefix() {
    String detail =
        CtbClient.nonJsonDetail(
            "RecipeBook/GetAllRecipes",
            302,
            "text/html; charset=utf-8",
            "<html><head><title>Object moved</title></head></html>");

    assertThat(detail).contains("RecipeBook/GetAllRecipes");
    assertThat(detail).contains("HTTP 302");
    assertThat(detail).contains("text/html");
    assertThat(detail).contains("<html>");
  }

  @Test
  void nonJsonDetailIsBoundedForALargeBody() {
    String detail = CtbClient.nonJsonDetail("X", 500, "text/html", "a".repeat(10_000));
    assertThat(detail.length()).isLessThan(400);
  }

  @Test
  void emptyRequestBodyIsReplacedSoIisDoesNotReturn411() {
    // Java's HttpClient sends no Content-Length for an empty body; IIS replies 411 "Length
    // Required" with an HTML page. Every request must carry a non-empty form.
    assertThat(CtbClient.requestBody("")).isEqualTo("keyword=");
    assertThat(CtbClient.requestBody(null)).isEqualTo("keyword=");
    assertThat(CtbClient.requestBody("keyword=&start=0&limit=200"))
        .isEqualTo("keyword=&start=0&limit=200");
  }
}
