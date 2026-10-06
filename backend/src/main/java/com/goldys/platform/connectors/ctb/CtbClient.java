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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticated HTTP client for CTB's internal AJAX endpoints (ASP.NET MVC, session-cookie auth, no
 * public API).
 */
public class CtbClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger log = LoggerFactory.getLogger(CtbClient.class);

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
    // Establish the ASP.NET session first: the login POST alone does not set the session
    // cookie, but a GET to a session-backed page (this one 302s to /Default/Login) does.
    get("/Default/Home2");
    String body = post("/Account/Login", form("userEmail", email, "userPassword", password));
    if (!isSuccess(body)) {
      throw new ConnectorFetchException("CONNECTOR_AUTH_FAILED", "CTB login failed");
    }
  }

  /**
   * True when a CTB response reports success. CTB uses two envelopes: the login returns {@code
   * IsSuccess} at the top level, while data endpoints wrap it as {@code message: {IsSuccess, ...}}.
   * Accept either.
   */
  static boolean isSuccess(String body) {
    return isSuccess(parse(body));
  }

  private static boolean isSuccess(JsonNode json) {
    // CTB's success signal varies by endpoint. The login returns a top-level IsSuccess; the
    // older data envelope used message.IsSuccess; the current data endpoints return `data`
    // with no IsSuccess field at all. Prefer an explicit IsSuccess flag when present, and
    // otherwise treat a response without one as success (the data-endpoint shape).
    JsonNode topIsSuccess = json.get("IsSuccess");
    if (topIsSuccess != null && topIsSuccess.isBoolean()) {
      return topIsSuccess.asBoolean();
    }
    JsonNode nestedIsSuccess = json.path("message").get("IsSuccess");
    if (nestedIsSuccess != null && nestedIsSuccess.isBoolean()) {
      return nestedIsSuccess.asBoolean();
    }
    return true;
  }

  /** One page of revenue rows plus the total record count, returned as raw JSON for the sink. */
  public CtbPage searchRevenues(int start, int limit) {
    String body =
        post(
            "/Revenue/SearchRevenues",
            form("keyword", "", "start", String.valueOf(start), "limit", String.valueOf(limit)));
    JsonNode json = parse(body);
    if (!isSuccess(json)) {
      log.warn("CTB revenue search failed; response: {}", body);
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "CTB revenue search failed: " + truncate(body, 500));
    }
    int total = json.path("totalCount").asInt(json.path("data").size());
    return new CtbPage(body, total);
  }

  /** One page of sale items plus the total count, as raw JSON for the sink. */
  public CtbPage searchSaleItems(String fromDate, String toDate, int start, int limit) {
    String body =
        post(
            "/Sale/SearchSaleItemsByDateRange",
            form(
                "fromDate",
                fromDate,
                "toDate",
                toDate,
                "searchType",
                "-1",
                "start",
                String.valueOf(start),
                "limit",
                String.valueOf(limit)));
    JsonNode json = parse(body);
    if (!isSuccess(json)) {
      log.warn("CTB sale-item search failed; response: {}", body);
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "CTB sale-item search failed: " + truncate(body, 500));
    }
    int total = json.path("totalCount").asInt(json.path("data").size());
    return new CtbPage(body, total);
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  private String get(String path) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Accept", "text/html,application/xhtml+xml")
            .GET()
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
