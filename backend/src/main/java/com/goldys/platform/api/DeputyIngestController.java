package com.goldys.platform.api;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the Deputy webhook payload and stores it byte-faithfully, unparsed.
 *
 * <p>Deputy's schema is unconfirmed, so this endpoint deliberately does no canonicalization: it
 * accepts any {@code content-type} and any body, recording it in the raw ledger for later parsing
 * once the first payload is observed. Public (no OIDC session), gated by a shared token in the
 * {@code X-Webhook-Token} header or {@code token} query param. Set {@code deputy.drop-token} in
 * production; while unset the endpoint rejects every request (fail closed).
 */
@RestController
@RequestMapping("/api/ingest")
public class DeputyIngestController {
  private final IngestionService ingestion;
  private final String dropToken;

  public DeputyIngestController(
      IngestionService ingestion, @Value("${deputy.drop-token:}") String dropToken) {
    this.ingestion = ingestion;
    this.dropToken = dropToken;
  }

  @PostMapping("/deputy")
  ResponseEntity<Void> deputy(
      @RequestBody byte[] body,
      @RequestHeader(value = "Content-Type", defaultValue = "application/octet-stream")
          String contentType,
      @RequestHeader(value = "X-Webhook-Token", required = false) String token,
      @RequestParam(value = "token", required = false) String queryToken) {
    String provided = StringUtils.hasText(token) ? token : queryToken;
    if (!tokenValid(provided)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    ingestion.ingestPush(
        "DEPUTY", "deputy-webhook", FetchMethod.API, contentType, body, null, "deputy-webhook");
    return ResponseEntity.accepted().build();
  }

  private boolean tokenValid(String token) {
    if (dropToken == null || dropToken.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        dropToken.getBytes(StandardCharsets.UTF_8),
        (token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
  }
}
