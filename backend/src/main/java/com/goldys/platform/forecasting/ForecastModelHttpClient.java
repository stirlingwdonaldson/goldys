package com.goldys.platform.forecasting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * JDK {@link HttpClient} adapter for the self-hosted forecasting runtime.
 *
 * <p>The service builds the full feature matrix and returns {@code POST {baseUrl}/forecast} as
 * {@code {forecast: per-day rows, diagnostics, warnings}}. The request body is assembled explicitly
 * (rather than serialising the record directly) so the wire key stays {@code booked_covers} rather
 * than the record's {@code bookedCovers}.
 *
 * <p>Failures — non-2xx, IO, or an unparseable body — surface as {@link ForecastModelException}
 * with an operator-facing message that never carries the request payload, credentials, or tokens.
 */
@Component
public class ForecastModelHttpClient implements ForecastModelClient {

  private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

  private final String baseUrl;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public ForecastModelHttpClient(TimesFmProperties properties, ObjectMapper mapper) {
    this.baseUrl = properties.baseUrl();
    this.mapper = mapper;
    // Pin HTTP/1.1: the default builder sends `Connection: Upgrade, HTTP2-Settings` and
    // `Upgrade: h2c` headers (an HTTP/2 cleartext upgrade attempt). Uvicorn, which serves the
    // self-hosted service, treats that as a protocol switch it doesn't support and drops
    // the request body, so every forecast came back as HTTP 422 "Field required".
    this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  }

  @Override
  public ForecastModelResponse forecast(ForecastModelRequest request) {
    HttpRequest httpRequest =
        HttpRequest.newBuilder(URI.create(baseUrl + "/forecast"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(READ_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(serialize(request)))
            .build();

    HttpResponse<String> response;
    try {
      response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new ForecastModelException("forecasting service request failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ForecastModelException("Interrupted while calling the forecasting service", e);
    }

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new ForecastModelException(
          "forecasting service returned HTTP " + response.statusCode());
    }

    try {
      JsonNode root = mapper.readTree(response.body());
      // `diagnostics` is a JSON object on the wire but is carried as a raw JSON string so the
      // caller can persist it as-is (ForecastRun.diagnostics is a JSONB String). It is re-parsed
      // later with Jackson, so the compact `toString()` form is exactly what a caller re-parses.
      List<ForecastModelResponse.Day> forecast =
          mapper.treeToValue(
              root.get("forecast"), new TypeReference<List<ForecastModelResponse.Day>>() {});
      String diagnostics =
          root.hasNonNull("diagnostics") ? root.get("diagnostics").toString() : null;
      List<String> warnings =
          mapper.treeToValue(root.get("warnings"), new TypeReference<List<String>>() {});
      return new ForecastModelResponse(forecast, diagnostics, warnings);
    } catch (IOException | IllegalArgumentException e) {
      throw new ForecastModelException("forecasting service returned an unparseable response", e);
    }
  }

  private String serialize(ForecastModelRequest request) {
    try {
      ObjectNode body = mapper.createObjectNode();
      body.set("dates", mapper.valueToTree(request.dates()));
      body.set("net", mapper.valueToTree(request.net()));
      body.set("booked_covers", mapper.valueToTree(request.bookedCovers()));
      body.set("bookings", mapper.valueToTree(request.bookings()));
      body.set("food", mapper.valueToTree(request.food()));
      body.set("bev", mapper.valueToTree(request.bev()));
      body.put("horizon", request.horizon());
      body.set("quantiles", mapper.valueToTree(request.quantiles()));
      return mapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new ForecastModelException("Failed to build the forecasting request", e);
    }
  }
}
