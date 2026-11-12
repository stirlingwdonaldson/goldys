package com.goldys.platform.connectors.lightspeed;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Decodes the shared Looker envelope after the original bytes have reached the raw ledger. */
final class LightspeedEnvelope {
  // Historical exports exceed Jackson's default string limit. Keep the existing bulk policy;
  // imposing a smaller ingress limit needs measured delivery sizes and a separate rollout.
  private static final ObjectMapper MAPPER =
      new ObjectMapper(
          JsonFactory.builder()
              .streamReadConstraints(
                  StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
              .build());

  private LightspeedEnvelope() {}

  static byte[] csv(byte[] body) {
    try {
      JsonNode root = MAPPER.readTree(body);
      JsonNode data = root == null ? null : root.path("attachment").path("data");
      if (data == null || !data.isTextual()) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH",
            "No textual attachment.data in Lightspeed webhook payload");
      }
      return data.textValue().getBytes(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON webhook payload", e);
    }
  }
}
