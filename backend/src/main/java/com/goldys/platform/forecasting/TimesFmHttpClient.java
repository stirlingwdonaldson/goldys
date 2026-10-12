package com.goldys.platform.forecasting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * JDK {@link HttpClient} adapter for the self-hosted TimesFM inference service.
 *
 * <p>The service is data-agnostic: {@code POST {baseUrl}/forecast} takes {@code {dates, target,
 * covariates, horizon, freq:"D", quantiles}} and returns {@code {dates, quantiles}}. The request
 * body is assembled explicitly (rather than serialising the record directly) so the wire keys stay
 * {@code horizon} and {@code freq} rather than the record's {@code horizonDays} and a missing
 * frequency.
 *
 * <p>Failures — non-2xx, IO, or an unparseable body — surface as {@link TimesFmException} with an
 * operator-facing message that never carries the request payload, credentials, or tokens.
 */
@Component
public class TimesFmHttpClient implements TimesFmClient {

  private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

  private final String baseUrl;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public TimesFmHttpClient(TimesFmProperties properties, ObjectMapper mapper) {
    this.baseUrl = properties.baseUrl();
    this.mapper = mapper;
    this.http = HttpClient.newBuilder().build();
  }

  @Override
  public TimesFmForecast forecast(TimesFmRequest request) {
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
      throw new TimesFmException("TimesFM service request failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TimesFmException("Interrupted while calling the TimesFM service", e);
    }

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new TimesFmException("TimesFM service returned HTTP " + response.statusCode());
    }

    try {
      return mapper.readValue(response.body(), TimesFmForecast.class);
    } catch (IOException e) {
      throw new TimesFmException("TimesFM service returned an unparseable response", e);
    }
  }

  private String serialize(TimesFmRequest request) {
    try {
      ObjectNode body = mapper.createObjectNode();
      body.set("dates", mapper.valueToTree(request.dates()));
      body.set("target", mapper.valueToTree(request.target()));
      body.set("covariates", mapper.valueToTree(request.covariates()));
      body.put("horizon", request.horizonDays());
      body.put("freq", "D");
      body.set("quantiles", mapper.valueToTree(request.quantiles()));
      return mapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new TimesFmException("Failed to build the TimesFM request", e);
    }
  }
}
