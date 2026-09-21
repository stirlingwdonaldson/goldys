package com.goldys.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.connectors.lightspeed.LightspeedIngestService;
import com.goldys.platform.connectors.lightspeed.LightspeedProductIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the public webhook endpoints through the real {@link SecurityConfig} (unlike {@link
 * LightspeedIngestControllerTest}, which disables filters) to prove the permitAll matchers work for
 * bodyless POSTs — the request must reach the controller and be rejected there (401), never be
 * redirected to /login (302).
 */
@WebMvcTest(LightspeedIngestController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "lightspeed.webhook-token=test-token")
class LightspeedIngestControllerSecurityTest {

  @Autowired MockMvc mvc;

  @MockitoBean LightspeedIngestService ingestService;
  @MockitoBean LightspeedProductIngestService productIngestService;

  @Test
  void bodylessWebhookPostIsRejectedNotRedirected() throws Exception {
    // No body → 400 "Required request body is missing" (reached the controller), never a 302.
    mvc.perform(post("/api/ingest/lightspeed")).andExpect(status().is4xxClientError());
  }

  @Test
  void bodylessProductWebhookPostIsRejectedNotRedirected() throws Exception {
    mvc.perform(post("/api/ingest/lightspeed-products")).andExpect(status().is4xxClientError());
  }
}
