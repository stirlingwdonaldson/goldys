package com.goldys.platform.connectors.opentable;

import static com.goldys.platform.connectors.opentable.session.FakeOpenTableBrowser.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.connectors.opentable.session.BrowserResponse;
import com.goldys.platform.connectors.opentable.session.FakeOpenTableBrowser;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenTableApiClientTest {
  private final FakeOpenTableBrowser browser = new FakeOpenTableBrowser();
  private final List<Duration> sleeps = new ArrayList<>();
  private final OpenTableApiClient client =
      new OpenTableApiClient(browser, Duration.ofMillis(1500), sleeps::add);

  @Test
  void spacesEveryRequest() {
    client.get("/gateway/a");
    client.get("/gateway/b");

    assertThat(sleeps).containsExactly(Duration.ofMillis(1500), Duration.ofMillis(1500));
  }

  @Test
  void backsOffOnRateLimitThenSucceeds() {
    browser.queued.add(json(429, "{}"));
    browser.queued.add(json(503, "{}"));
    browser.queued.add(json(200, "{\"ok\":true}"));

    BrowserResponse response = client.get("/gateway/a");

    assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
    assertThat(sleeps).contains(Duration.ofSeconds(5), Duration.ofSeconds(10));
    assertThat(browser.requests).hasSize(3);
  }

  @Test
  void givesUpAsRateLimitedAfterMaxAttempts() {
    browser.router = path -> json(429, "{}");

    assertThatThrownBy(() -> client.get("/gateway/a"))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_RATE_LIMITED"));
    assertThat(browser.requests).hasSize(OpenTableApiClient.MAX_ATTEMPTS);
    assertThat(sleeps).contains(OpenTableApiClient.MAX_BACKOFF);
  }

  @Test
  void unauthorizedIsAuthFailureWithoutRetry() {
    browser.router = path -> json(401, "{}");

    assertThatThrownBy(() -> client.get("/gateway/a"))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_AUTH_FAILED"));
    assertThat(browser.requests).hasSize(1);
  }

  @Test
  void htmlResponseIsSchemaMismatchAndBodyIsNotEchoed() {
    browser.router =
        path ->
            new BrowserResponse(
                200,
                "text/html",
                "<html>secret guest page</html>".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> client.get("/gateway/a"))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> {
              assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH");
              assertThat(e.getMessage()).doesNotContain("secret");
            });
  }

  @Test
  void sidecarDownIsBrowserFailure() {
    browser.unavailable = true;

    assertThatThrownBy(() -> client.get("/gateway/a"))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_BROWSER_FAILED"));
  }
}
