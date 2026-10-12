package com.goldys.platform.forecasting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TimesFmHttpClientTest {

  private static final String RESPONSE_JSON =
      "{\"dates\":[\"2026-10-13\",\"2026-10-14\"],"
          + "\"quantiles\":{\"0.1\":[10.0,11.0],\"0.5\":[12.0,13.0],\"0.9\":[14.0,15.0]}}";

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Test
  void requestSerializesContract() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/forecast",
        exchange -> {
          captured.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response = RESPONSE_JSON.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      TimesFmHttpClient client = client("http://localhost:" + server.getAddress().getPort());

      TimesFmForecast forecast = client.forecast(request());

      assertThat(forecast.dates())
          .containsExactly(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14));
      assertThat(forecast.quantiles()).containsOnlyKeys("0.1", "0.5", "0.9");
      assertThat(forecast.quantiles().get("0.5"))
          .containsExactly(new BigDecimal("12.0"), new BigDecimal("13.0"));

      JsonNode body = MAPPER.readTree(captured.get());
      assertThat(body.get("dates").get(0).asText()).isEqualTo("2026-10-11");
      assertThat(body.get("dates").get(1).asText()).isEqualTo("2026-10-12");
      assertThat(body.get("target").get(0).decimalValue()).isEqualByComparingTo("100.0");
      assertThat(body.get("target").get(1).decimalValue()).isEqualByComparingTo("110.0");
      JsonNode covariate = body.get("covariates").get(0);
      assertThat(covariate.get("name").asText()).isEqualTo("covers");
      assertThat(covariate.get("values").get(0).decimalValue()).isEqualByComparingTo("50");
      assertThat(body.get("horizon").asInt()).isEqualTo(2);
      assertThat(body.get("freq").asText()).isEqualTo("D");
      assertThat(body.get("quantiles").get(0).asText()).isEqualTo("0.1");
      assertThat(body.get("quantiles").get(1).asText()).isEqualTo("0.5");
      assertThat(body.get("quantiles").get(2).asText()).isEqualTo("0.9");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void non2xxThrows() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/forecast",
        exchange -> {
          byte[] response = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(500, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      TimesFmHttpClient client = client("http://localhost:" + server.getAddress().getPort());

      TimesFmException ex = (TimesFmException) catchThrowable(() -> client.forecast(request()));

      assertThat(ex).isNotNull();
      assertThat(ex.getMessage()).isEqualTo("TimesFM service returned HTTP 500");
      assertThat(ex.getMessage())
          .doesNotContain("2026-10-11")
          .doesNotContain("covers")
          .doesNotContain("100.0");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void unreachableThrows() throws Exception {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    TimesFmHttpClient client = client("http://localhost:" + closedPort);

    TimesFmException ex = (TimesFmException) catchThrowable(() -> client.forecast(request()));

    assertThat(ex).isNotNull();
    assertThat(ex.getMessage())
        .doesNotContain("2026-10-11")
        .doesNotContain("covers")
        .doesNotContain("100.0");
  }

  @Test
  void nullsSerializeAsJsonNull() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/forecast",
        exchange -> {
          captured.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response = RESPONSE_JSON.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      TimesFmHttpClient client = client("http://localhost:" + server.getAddress().getPort());
      TimesFmRequest request =
          new TimesFmRequest(
              List.of(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12)),
              Arrays.asList(new BigDecimal("100.0"), null),
              List.of(
                  new TimesFmRequest.Covariate(
                      "covers", Arrays.asList(null, new BigDecimal("55")))),
              2,
              List.of("0.1", "0.5", "0.9"));

      client.forecast(request);

      JsonNode body = MAPPER.readTree(captured.get());
      assertThat(body.get("target").get(0).decimalValue()).isEqualByComparingTo("100.0");
      assertThat(body.get("target").get(1).isNull()).isTrue();
      JsonNode values = body.get("covariates").get(0).get("values");
      assertThat(values.size()).isEqualTo(2);
      assertThat(values.get(0).isNull()).isTrue();
      assertThat(values.get(1).decimalValue()).isEqualByComparingTo("55");
    } finally {
      server.stop(0);
    }
  }

  private static TimesFmHttpClient client(String baseUrl) {
    return new TimesFmHttpClient(new TimesFmProperties(false, baseUrl), MAPPER);
  }

  private static TimesFmRequest request() {
    return new TimesFmRequest(
        List.of(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12)),
        List.of(new BigDecimal("100.0"), new BigDecimal("110.0")),
        List.of(new TimesFmRequest.Covariate("covers", List.of(new BigDecimal("50")))),
        2,
        List.of("0.1", "0.5", "0.9"));
  }
}
