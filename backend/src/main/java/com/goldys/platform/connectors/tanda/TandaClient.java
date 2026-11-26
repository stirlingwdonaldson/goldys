package com.goldys.platform.connectors.tanda;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fixed-origin, GET-only transport. Never retain source error bodies or credential-bearing causes.
 */
class TandaClient {
  private final HttpClient client;

  TandaClient() {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  TandaClient(HttpClient client) {
    this.client = client;
  }

  byte[] get(String path, String token) {
    try {
      // Leave headroom below Tanda's shared 200 requests/minute allowance.
      Thread.sleep(400);
      var request =
          HttpRequest.newBuilder(URI.create("https://my.tanda.co/api/v2/" + path))
              .timeout(Duration.ofSeconds(45))
              .header("Authorization", "Bearer " + token)
              .header("Accept", "application/json")
              .GET()
              .build();
      var response =
          client.send(
              request,
              HttpResponse.BodyHandlers.limiting(
                  HttpResponse.BodyHandlers.ofByteArray(), 16 * 1024 * 1024));
      int status = response.statusCode();
      if (status != 200) {
        String type =
            switch (status) {
              case 401 -> "TANDA_TOKEN_REJECTED";
              case 403 -> "TANDA_ACCESS_DENIED";
              case 429 -> "TANDA_RATE_LIMITED";
              default -> "TANDA_HTTP_ERROR";
            };
        throw new ConnectorFetchException(
            type,
            "Tanda returned HTTP "
                + status
                + ". Check token access or retry later; retained pages are preserved.");
      }
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorFetchException(
          "TANDA_INTERRUPTED", "Tanda capture interrupted; retry when ready.");
    } catch (IOException e) {
      throw new ConnectorFetchException(
          "TANDA_NETWORK_ERROR",
          "Tanda response could not be read within the request or size limits; retry or review capture limits.");
    }
  }
}
