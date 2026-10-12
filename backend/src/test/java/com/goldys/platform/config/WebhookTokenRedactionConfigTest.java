package com.goldys.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WebhookTokenRedactionConfigTest {

  @Test
  void stripsTokenFromUrlLeavingOtherParams() {
    assertEquals(
        "https://host/api/ingest/lightspeed?a=1&b=2",
        WebhookTokenRedactionConfig.stripTokenFromUrl(
            "https://host/api/ingest/lightspeed?token=SECRET&a=1&b=2"));
  }

  @Test
  void stripsTokenWhenItIsTheOnlyParam() {
    assertEquals(
        "https://host/api/ingest/lightspeed",
        WebhookTokenRedactionConfig.stripTokenFromUrl(
            "https://host/api/ingest/lightspeed?token=SECRET"));
  }

  @Test
  void leavesUrlWithoutQueryUntouched() {
    assertEquals(
        "https://host/api/ingest/lightspeed",
        WebhookTokenRedactionConfig.stripTokenFromUrl("https://host/api/ingest/lightspeed"));
  }

  @Test
  void stripsTokenFromBareQueryString() {
    assertEquals(
        "a=1&c=3", WebhookTokenRedactionConfig.stripQueryParam("token=SECRET&a=1&c=3", "token"));
  }

  @Test
  void bareQueryStringWithOnlyTokenBecomesEmpty() {
    assertEquals("", WebhookTokenRedactionConfig.stripQueryParam("token=SECRET", "token"));
  }

  @Test
  void nullQueryStringStaysNull() {
    assertNull(WebhookTokenRedactionConfig.stripQueryParam(null, "token"));
  }

  @Test
  void scrubTokenHandlesBareQueryStringWithoutQuestionMark() {
    assertEquals("a=1&c=3", WebhookTokenRedactionConfig.scrubToken("a=1&token=SECRET&c=3"));
  }

  @Test
  void scrubTokenLeavesStringWithoutTokenUntouched() {
    assertEquals(
        "https://host/api/dashboard?from=2026-09-01",
        WebhookTokenRedactionConfig.scrubToken("https://host/api/dashboard?from=2026-09-01"));
  }
}
