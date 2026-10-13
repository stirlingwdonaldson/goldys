package com.goldys.platform.connectors.opentable.session;

/**
 * An in-page fetch result. {@code body} is the exact response bytes (read as a Blob, not re-encoded
 * text), so it can go to the raw ledger byte-faithfully. A {@code status} of 0 means the request
 * failed before any HTTP response (network error, CORS, navigation away).
 */
public record BrowserResponse(int status, String contentType, byte[] body) {
  public BrowserResponse {
    body = body == null ? new byte[0] : body.clone();
  }

  @Override
  public byte[] body() {
    return body.clone();
  }

  public boolean isJson() {
    return contentType != null && contentType.toLowerCase().contains("json");
  }
}
