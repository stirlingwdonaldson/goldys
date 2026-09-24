package com.goldys.platform.api;

import com.goldys.platform.connectors.opentable.OpenTableCsvIngestService;
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
 * Receives a manually-exported GuestCenter reservations CSV and ingests it.
 *
 * <p>Public (no OIDC session), gated by a shared token in the {@code X-Webhook-Token} header or
 * {@code token} query param. Set {@code opentable.drop-token} in production; while unset the
 * endpoint rejects every request (fail closed).
 */
@RestController
@RequestMapping("/api/ingest")
public class OpenTableCsvIngestController {
  private final OpenTableCsvIngestService ingestService;
  private final String dropToken;

  public OpenTableCsvIngestController(
      OpenTableCsvIngestService ingestService,
      @Value("${opentable.drop-token:}") String dropToken) {
    this.ingestService = ingestService;
    this.dropToken = dropToken;
  }

  @PostMapping("/opentable")
  ResponseEntity<Void> opentable(
      @RequestBody byte[] body,
      @RequestHeader(value = "X-Webhook-Token", required = false) String token,
      @RequestParam(value = "token", required = false) String queryToken) {
    String provided = StringUtils.hasText(token) ? token : queryToken;
    if (!tokenValid(provided)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    ingestService.ingest(body);
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
