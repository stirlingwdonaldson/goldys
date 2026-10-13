package com.goldys.platform.connectors.opentable;

import com.goldys.platform.connectors.opentable.cdp.CdpException;
import com.goldys.platform.connectors.opentable.session.BrowserResponse;
import com.goldys.platform.connectors.opentable.session.OpenTableBrowser;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.time.Duration;

/**
 * Calls GuestCenter's JSON endpoints through the signed-in browser, paced and classified.
 *
 * <p>GuestCenter's reporting endpoints answered 429 under burst load during discovery, and ~1.5 s
 * spacing with exponential backoff was clean. Every request waits at least {@code spacing} after
 * the previous one; 429 / 5xx / network errors back off 5 s doubling to 90 s for up to 10 attempts.
 * Failure messages are generic and never include the response body.
 */
public class OpenTableApiClient {
  static final int MAX_ATTEMPTS = 10;
  static final Duration FIRST_BACKOFF = Duration.ofSeconds(5);
  static final Duration MAX_BACKOFF = Duration.ofSeconds(90);

  /** Sleeping is injected so tests can run the retry logic without waiting. */
  @FunctionalInterface
  public interface Sleeper {
    void sleep(Duration duration);
  }

  private final OpenTableBrowser browser;
  private final Duration spacing;
  private final Sleeper sleeper;

  public OpenTableApiClient(OpenTableBrowser browser, Duration spacing, Sleeper sleeper) {
    this.browser = browser;
    this.spacing = spacing;
    this.sleeper = sleeper;
  }

  public boolean isAuthenticated() {
    try {
      return browser.isAuthenticated();
    } catch (CdpException e) {
      throw browserFailed(e);
    }
  }

  public BrowserResponse get(String path) {
    return send("GET", path, null);
  }

  public BrowserResponse post(String path, String jsonBody) {
    return send("POST", path, jsonBody);
  }

  private BrowserResponse send(String method, String path, String jsonBody) {
    Duration backoff = FIRST_BACKOFF;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      sleeper.sleep(spacing);
      BrowserResponse response;
      try {
        response = browser.fetch(method, path, jsonBody);
      } catch (CdpException e) {
        throw browserFailed(e);
      }
      int status = response.status();
      if (status == 401 || status == 403) {
        throw new ConnectorFetchException(
            "CONNECTOR_AUTH_FAILED",
            "OpenTable session ended (HTTP "
                + status
                + "); sign in again from Data health → Sync OpenTable");
      }
      if (status == 429 || status >= 500 || status == 0) {
        sleeper.sleep(backoff);
        backoff =
            backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0
                ? MAX_BACKOFF
                : backoff.multipliedBy(2);
        continue;
      }
      if (status != 200) {
        throw new ConnectorFetchException(
            "CONNECTOR_FETCH_FAILED", "OpenTable endpoint returned HTTP " + status);
      }
      if (!response.isJson()) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "OpenTable endpoint returned a non-JSON response");
      }
      return response;
    }
    throw new ConnectorFetchException(
        "CONNECTOR_RATE_LIMITED",
        "OpenTable kept refusing requests after " + MAX_ATTEMPTS + " attempts");
  }

  private static ConnectorFetchException browserFailed(CdpException e) {
    return new ConnectorFetchException(
        "CONNECTOR_BROWSER_FAILED", "OpenTable browser sidecar unavailable: " + e.getMessage(), e);
  }
}
