package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Authenticated HTTP client for CTB's internal AJAX endpoints (ASP.NET MVC, session-cookie auth, no
 * public API).
 */
public class CtbClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String baseUrl;
  private final HttpClient http;

  public CtbClient(String baseUrl) {
    this.baseUrl = baseUrl;
    this.http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
  }

  public void login(String email, String password) {
    String body = post("/Account/Login", form("userEmail", email, "userPassword", password));
    JsonNode json = parse(body);
    if (!json.path("message").path("IsSuccess").asBoolean(false)) {
      throw new ConnectorFetchException("CONNECTOR_AUTH_FAILED", "CTB login failed");
    }
  }

  /** One page of revenue rows plus the total record count, returned as raw JSON for the sink. */
  public CtbPage searchRevenues(int start, int limit) {
    String body =
        post(
            "/Revenue/SearchRevenues",
            form("keyword", "", "start", String.valueOf(start), "limit", String.valueOf(limit)));
    JsonNode json = parse(body);
    if (!json.path("message").path("IsSuccess").asBoolean(false)) {
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "CTB revenue search failed");
    }
    int total = json.path("totalCount").asInt(json.path("data").size());
    return new CtbPage(body, total);
  }

  private String post(String path, String form) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*")
            .header("Referer", baseUrl + "/Default/Home2")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "CTB request failed: " + path, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "Interrupted: " + path, e);
    }
  }

  private static String form(String... kv) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < kv.length; i += 2) {
      if (i > 0) sb.append('&');
      sb.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8));
      sb.append('=');
      sb.append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
    }
    return sb.toString();
  }

  private static JsonNode parse(String body) {
    try {
      return MAPPER.readTree(body);
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response", e);
    }
  }

  /** Raw JSON body of one page plus the total record count across all pages. */
  public record CtbPage(String json, int totalCount) {}
}
