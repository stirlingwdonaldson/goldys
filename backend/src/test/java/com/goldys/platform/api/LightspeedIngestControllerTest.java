package com.goldys.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.connectors.lightspeed.LightspeedIngestService;
import com.goldys.platform.connectors.lightspeed.LightspeedProductIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LightspeedIngestController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "lightspeed.webhook-token=test-token")
class LightspeedIngestControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean LightspeedIngestService ingestService;
  @MockitoBean LightspeedProductIngestService productIngestService;

  @Test
  void queryParamTokenIsAccepted() throws Exception {
    mvc.perform(
            post("/api/ingest/lightspeed?token=test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isAccepted());
  }

  @Test
  void missingTokenIsRejected() throws Exception {
    mvc.perform(
            post("/api/ingest/lightspeed").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }
}
