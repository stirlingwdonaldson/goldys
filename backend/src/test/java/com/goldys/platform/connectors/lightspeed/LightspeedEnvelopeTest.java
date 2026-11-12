package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LightspeedEnvelopeTest {
  @Test
  void decodesEscapesAndUnicodeWithoutChangingCsvBytes() throws Exception {
    String csv = "name,note\r\nCafé,\"quoted \\\"word\\\"\"\r\n";
    byte[] body = new ObjectMapper().writeValueAsBytes(Map.of("attachment", Map.of("data", csv)));
    assertThat(LightspeedEnvelope.csv(body)).isEqualTo(csv.getBytes(StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "null",
        "{}",
        "not-json",
        "{\"attachment\":{\"data\":12}}",
        "{\"attachment\":{\"data\":null}}",
        "{\"attachment\":{\"data\":{}}}"
      })
  void rejectsInvalidEnvelopesWithSafeOperatorMessage(String body) {
    assertThatThrownBy(() -> LightspeedEnvelope.csv(body.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageMatching(
            "No textual attachment.data in Lightspeed webhook payload|Non-JSON webhook payload");
  }

  @Test
  void acceptsHistoricalExportsAboveDefaultJacksonStringLimit() throws Exception {
    String csv = "x".repeat(21 * 1024 * 1024);
    byte[] body = new ObjectMapper().writeValueAsBytes(Map.of("attachment", Map.of("data", csv)));
    assertThat(LightspeedEnvelope.csv(body)).hasSize(csv.length());
  }

  @Test
  void emptyCsvRemainsAnEmptyDelivery() {
    assertThat(
            LightspeedEnvelope.csv(
                "{\"attachment\":{\"data\":\"\"}}".getBytes(StandardCharsets.UTF_8)))
        .isEmpty();
  }
}
