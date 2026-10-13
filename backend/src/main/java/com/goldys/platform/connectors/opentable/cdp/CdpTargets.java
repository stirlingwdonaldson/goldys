package com.goldys.platform.connectors.opentable.cdp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Finds (or opens) the sidecar's page target and returns its DevTools WebSocket URL.
 *
 * <p>DevTools rejects HTTP requests whose {@code Host} header is a hostname rather than an IP or
 * {@code localhost} (a DNS-rebinding guard), and the JDK client won't let us override {@code Host}.
 * So the sidecar's service name is resolved to its container IP first and every request goes to the
 * IP. The returned WebSocket URL is rewritten to the same authority, because Chromium reports its
 * own loopback bind address and port, which aren't reachable from the backend.
 */
public final class CdpTargets {
  private final HttpClient http;
  private final ObjectMapper json;
  private final Duration timeout;

  public CdpTargets(HttpClient http, ObjectMapper json, Duration timeout) {
    this.http = http;
    this.json = json;
    this.timeout = timeout;
  }

  public URI pageWebSocket(String host, int port) {
    String authority = resolve(host) + ":" + port;
    JsonNode targets = getJson("http://" + authority + "/json/list", "GET");
    for (JsonNode target : targets) {
      if ("page".equals(target.path("type").asText())) {
        return rewrite(target.path("webSocketDebuggerUrl").asText(), authority);
      }
    }
    // No page (the last tab was closed): open a blank one. Chromium requires PUT for /json/new.
    JsonNode created = getJson("http://" + authority + "/json/new?about:blank", "PUT");
    return rewrite(created.path("webSocketDebuggerUrl").asText(), authority);
  }

  private static String resolve(String host) {
    try {
      return InetAddress.getByName(host).getHostAddress();
    } catch (UnknownHostException e) {
      throw new CdpException("Cannot resolve browser host", e);
    }
  }

  private static URI rewrite(String webSocketUrl, String authority) {
    if (webSocketUrl == null || webSocketUrl.isBlank()) {
      throw new CdpException("Browser target has no DevTools WebSocket URL");
    }
    URI original = URI.create(webSocketUrl);
    return URI.create("ws://" + authority + original.getRawPath());
  }

  private JsonNode getJson(String url, String method) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new CdpException("DevTools endpoint returned HTTP " + response.statusCode());
      }
      return json.readTree(response.body());
    } catch (IOException e) {
      throw new CdpException("DevTools endpoint unreachable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CdpException("Interrupted reaching DevTools endpoint", e);
    }
  }
}
