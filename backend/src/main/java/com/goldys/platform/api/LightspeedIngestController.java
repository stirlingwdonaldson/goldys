package com.goldys.platform.api;

import com.goldys.platform.connectors.lightspeed.LightspeedIngestService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the Lightspeed Insights scheduled-report webhook and ingests it.
 *
 * <p>The webhook is a server-to-server push with no OIDC session, so the endpoint is public and
 * gated by a shared token in the {@code X-Webhook-Token} header. Set {@code
 * lightspeed.webhook-token} in production; while unset the endpoint rejects every request (fail
 * closed).
 */
@RestController
@RequestMapping("/api/ingest")
public class LightspeedIngestController {
  private final LightspeedIngestService ingestService;
  private final String webhookToken;

  public LightspeedIngestController(
      LightspeedIngestService ingestService,
      @Value("${lightspeed.webhook-token:}") String webhookToken) {
    this.ingestService = ingestService;
    this.webhookToken = webhookToken;
  }

  @PostMapping("/lightspeed")
  ResponseEntity<Void> lightspeed(
      @RequestBody byte[] body,
      @RequestHeader(value = "X-Webhook-Token", required = false) String token) {
    if (!tokenValid(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    ingestService.ingest(body);
    return ResponseEntity.accepted().build();
  }

  private boolean tokenValid(String token) {
    if (webhookToken == null || webhookToken.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        webhookToken.getBytes(StandardCharsets.UTF_8),
        (token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
  }
}
