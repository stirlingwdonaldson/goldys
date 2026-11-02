package com.goldys.platform.api;

import com.goldys.platform.connectors.lightspeed.LightspeedReceiptEvidenceIngestService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class LightspeedReceiptMappingWebhookController {
  private final LightspeedReceiptEvidenceIngestService service;
  private final String token;

  public LightspeedReceiptMappingWebhookController(
      LightspeedReceiptEvidenceIngestService service,
      @Value("${lightspeed.webhook-token:}") String token) {
    this.service = service;
    this.token = token;
  }

  @PostMapping("/api/ingest/lightspeed-receipt-mapping")
  public ResponseEntity<Void> ingest(
      @RequestBody byte[] body,
      @RequestParam long siteNumber,
      @RequestParam String siteName,
      @RequestHeader(value = "X-Webhook-Token", required = false) String header,
      @RequestParam(value = "token", required = false) String query) {
    String provided = header != null && !header.isBlank() ? header : query;
    if (token.isBlank()
        || provided == null
        || !MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    service.ingestMappingWebhook(body, siteNumber, siteName);
    return ResponseEntity.accepted().build();
  }
}
