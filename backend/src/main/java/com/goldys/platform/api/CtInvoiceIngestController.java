package com.goldys.platform.api;

import com.goldys.platform.connectors.ctb.CtInvoiceCsvIngestService;
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
 * Receives manually-exported CTB invoice CSV (metadata) and ingests it. Public (no OIDC session),
 * gated by a shared token in the {@code X-Webhook-Token} header or {@code token} query param. Set
 * {@code ctb.drop-token} in production; while unset the endpoint rejects every request (fail
 * closed).
 */
@RestController
@RequestMapping("/api/ingest")
public class CtInvoiceIngestController {
  private final CtInvoiceCsvIngestService csvIngest;
  private final String dropToken;

  public CtInvoiceIngestController(
      CtInvoiceCsvIngestService csvIngest, @Value("${ctb.drop-token:}") String dropToken) {
    this.csvIngest = csvIngest;
    this.dropToken = dropToken;
  }

  @PostMapping("/ctb-invoices")
  ResponseEntity<Void> invoices(
      @RequestBody byte[] body,
      @RequestHeader(value = "X-Webhook-Token", required = false) String token,
      @RequestParam(value = "token", required = false) String queryToken) {
    if (!tokenValid(token, queryToken)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    csvIngest.ingest(body);
    return ResponseEntity.accepted().build();
  }

  private boolean tokenValid(String token, String queryToken) {
    String provided = StringUtils.hasText(token) ? token : queryToken;
    if (dropToken == null || dropToken.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        dropToken.getBytes(StandardCharsets.UTF_8),
        (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8));
  }
}
