package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.connectors.lightspeed.LightspeedReceiptEvidenceIngestService;
import org.junit.jupiter.api.Test;

class LightspeedReceiptMappingWebhookControllerTest {
  @Test
  void unsetOrWrongTokenFailsClosedAndHeaderTakesPrecedence() {
    var service = mock(LightspeedReceiptEvidenceIngestService.class);
    var unset = new LightspeedReceiptMappingWebhookController(service, "");
    assertThat(unset.ingest(new byte[0], 96181, "site", null, "").getStatusCode().value())
        .isEqualTo(401);
    var controller = new LightspeedReceiptMappingWebhookController(service, "test-token");
    assertThat(
            controller
                .ingest(new byte[0], 96181, "site", "wrong", "test-token")
                .getStatusCode()
                .value())
        .isEqualTo(401);
    verifyNoInteractions(service);
    byte[] bytes = {1};
    assertThat(controller.ingest(bytes, 96181, "site", null, "test-token").getStatusCode().value())
        .isEqualTo(202);
    verify(service).ingestMappingWebhook(bytes, 96181, "site");
  }
}
