package com.goldys.platform.api;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the Lightspeed Insights scheduled-report webhook and persists it byte-faithfully.
 *
 * <p>The webhook is a server-to-server push with no OIDC session, so the endpoint is public and
 * gated by a shared token (query param {@code ?token=...}). Set {@code lightspeed.webhook-token} in
 * production; the default is a development value.
 */
@RestController
@RequestMapping("/api/ingest")
public class LightspeedIngestController {
  private final IngestionService ingestion;
  private final String webhookToken;

  public LightspeedIngestController(
      IngestionService ingestion,
      @Value("${lightspeed.webhook-token:dev-webhook-token}") String webhookToken) {
    this.ingestion = ingestion;
    this.webhookToken = webhookToken;
  }

  @PostMapping("/lightspeed")
  ResponseEntity<Void> lightspeed(
      @RequestBody String body, @RequestParam(required = false) String token) {
    if (!webhookToken.equals(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    ingestion.ingestPush(
        "LIGHTSPEED",
        "lightspeed-insights",
        FetchMethod.FILE_EXPORT,
        "application/json",
        body.getBytes(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8.name(),
        "lightspeed-insights");
    return ResponseEntity.accepted().build();
  }
}
