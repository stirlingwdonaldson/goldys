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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ForecastModelHttpClientTest {

  private static final String RESPONSE_JSON =
      "{\"forecast\":["
          + "{\"date\":\"2026-10-16\",\"median\":4200.5,\"p10\":3900.0,\"p90\":4600.0,"
          + "\"food\":1900.0,\"bev\":2300.5,\"baseline\":4100.0,\"bookedCovers\":85.0,"
          + "\"publicHoliday\":0,\"publicHolidayName\":null,\"publicHolidayEve\":0,"
          + "\"dayAfterPublicHoliday\":1,\"schoolHoliday\":0,\"uniInSession\":1,"
          + "\"serviceTempMaxC\":21.4,\"serviceRainMm\":0.0,\"weatherSource\":\"open-meteo\"},"
          + "{\"date\":\"2026-10-17\",\"median\":null,\"p10\":null,\"p90\":null,"
          + "\"food\":null,\"bev\":null,\"baseline\":null,\"bookedCovers\":120.0,"
          + "\"publicHoliday\":1,\"publicHolidayName\":\"Grand Final Friday\",\"publicHolidayEve\":1,"
          + "\"dayAfterPublicHoliday\":0,\"schoolHoliday\":1,\"uniInSession\":0,"
          + "\"serviceTempMaxC\":null,\"serviceRainMm\":null,\"weatherSource\":null}"
          + "],"
          + "\"diagnostics\":{\"modelVersion\":\"timesfm-3.0\",\"trainedRows\":365,"
          + "\"reliable\":true,\"warnings\":[],\"beatsBaseline\":true},"
          + "\"warnings\":[\"XGBoost twin failed: boom\"]}";

  private static final String NULL_DIAGNOSTICS_JSON =
      "{\"forecast\":[{\"date\":\"2026-10-16\",\"median\":4200.5,\"p10\":null,\"p90\":null,"
          + "\"food\":null,\"bev\":null,\"baseline\":null,\"bookedCovers\":null,"
          + "\"publicHoliday\":0,\"publicHolidayName\":null,\"publicHolidayEve\":0,"
          + "\"dayAfterPublicHoliday\":0,\"schoolHoliday\":0,\"uniInSession\":0,"
          + "\"serviceTempMaxC\":null,\"serviceRainMm\":null,\"weatherSource\":null}],"
          + "\"diagnostics\":null,\"warnings\":[\"XGBoost twin failed: not enough rows\"]}";

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Test
  void parsesForecastAndDiagnostics() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/forecast",
        exchange -> {
          byte[] response = RESPONSE_JSON.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      ForecastModelHttpClient client = client("http://localhost:" + server.getAddress().getPort());

      ForecastModelResponse response = client.forecast(request());

      List<ForecastModelResponse.Day> days = response.forecast();
      assertThat(days).hasSize(2);

      ForecastModelResponse.Day first = days.get(0);
      assertThat(first.date()).isEqualTo(LocalDate.of(2026, 10, 16));
      assertThat(first.median()).isEqualByComparingTo("4200.5");
      assertThat(first.p10()).isEqualByComparingTo("3900.0");
      assertThat(first.p90()).isEqualByComparingTo("4600.0");
      assertThat(first.food()).isEqualByComparingTo("1900.0");
      assertThat(first.bev()).isEqualByComparingTo("2300.5");
      assertThat(first.baseline()).isEqualByComparingTo("4100.0");
      assertThat(first.bookedCovers()).isEqualByComparingTo("85.0");
      assertThat(first.publicHoliday()).isZero();
      assertThat(first.publicHolidayName()).isNull();
      assertThat(first.dayAfterPublicHoliday()).isEqualTo(1);
      assertThat(first.uniInSession()).isEqualTo(1);
      assertThat(first.serviceTempMaxC()).isEqualByComparingTo("21.4");
      assertThat(first.weatherSource()).isEqualTo("open-meteo");

      ForecastModelResponse.Day second = days.get(1);
      assertThat(second.date()).isEqualTo(LocalDate.of(2026, 10, 17));
      assertThat(second.median()).isNull();
      assertThat(second.publicHoliday()).isEqualTo(1);
      assertThat(second.publicHolidayName()).isEqualTo("Grand Final Friday");
      assertThat(second.bookedCovers()).isEqualByComparingTo("120.0");

      assertThat(response.diagnostics()).isNotNull();
      JsonNode diagnostics = MAPPER.readTree(response.diagnostics());
      assertThat(diagnostics.get("modelVersion").asText()).isEqualTo("timesfm-3.0");
      assertThat(diagnostics.get("trainedRows").asInt()).isEqualTo(365);
      assertThat(diagnostics.get("reliable").asBoolean()).isTrue();

      assertThat(response.warnings()).containsExactly("XGBoost twin failed: boom");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void parsesNullDiagnosticsAndWarnings() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/forecast",
        exchange -> {
          byte[] response = NULL_DIAGNOSTICS_JSON.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      ForecastModelHttpClient client = client("http://localhost:" + server.getAddress().getPort());

      ForecastModelResponse response = client.forecast(request());

      assertThat(response.forecast()).hasSize(1);
      assertThat(response.forecast().get(0).median()).isEqualByComparingTo("4200.5");
      assertThat(response.diagnostics()).isNull();
      assertThat(response.warnings()).containsExactly("XGBoost twin failed: not enough rows");
    } finally {
      server.stop(0);
    }
  }

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
      ForecastModelHttpClient client = client("http://localhost:" + server.getAddress().getPort());

      client.forecast(request());

      JsonNode body = MAPPER.readTree(captured.get());
      assertThat(body.get("dates").get(0).asText()).isEqualTo("2026-10-11");
      assertThat(body.get("dates").get(1).asText()).isEqualTo("2026-10-12");
      assertThat(body.get("net").get(0).decimalValue()).isEqualByComparingTo("100.0");
      assertThat(body.get("net").get(1).decimalValue()).isEqualByComparingTo("110.0");
      assertThat(body.get("booked_covers").get(0).decimalValue()).isEqualByComparingTo("50");
      assertThat(body.get("bookings").get(0).decimalValue()).isEqualByComparingTo("40");
      assertThat(body.get("food").get(0).decimalValue()).isEqualByComparingTo("45.0");
      assertThat(body.get("bev").get(0).decimalValue()).isEqualByComparingTo("55.0");
      assertThat(body.get("horizon").asInt()).isEqualTo(2);
      assertThat(body.get("quantiles").get(0).asText()).isEqualTo("0.1");
      assertThat(body.get("quantiles").get(1).asText()).isEqualTo("0.5");
      assertThat(body.get("quantiles").get(2).asText()).isEqualTo("0.9");
    } finally {
      server.stop(0);
    }
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
      ForecastModelHttpClient client = client("http://localhost:" + server.getAddress().getPort());
      ForecastModelRequest request =
          new ForecastModelRequest(
              List.of(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12)),
              Arrays.asList(new BigDecimal("100.0"), null),
              Arrays.asList(null, new BigDecimal("55")),
              null,
              null,
              null,
              2,
              List.of("0.1", "0.5", "0.9"));

      client.forecast(request);

      JsonNode body = MAPPER.readTree(captured.get());
      assertThat(body.get("net").get(0).decimalValue()).isEqualByComparingTo("100.0");
      assertThat(body.get("net").get(1).isNull()).isTrue();
      JsonNode covers = body.get("booked_covers");
      assertThat(covers.size()).isEqualTo(2);
      assertThat(covers.get(0).isNull()).isTrue();
      assertThat(covers.get(1).decimalValue()).isEqualByComparingTo("55");
      assertThat(body.get("bookings").isNull()).isTrue();
      assertThat(body.get("food").isNull()).isTrue();
      assertThat(body.get("bev").isNull()).isTrue();
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
      ForecastModelHttpClient client = client("http://localhost:" + server.getAddress().getPort());

      ForecastModelException ex =
          (ForecastModelException) catchThrowable(() -> client.forecast(request()));

      assertThat(ex).isNotNull();
      assertThat(ex.getMessage()).isEqualTo("forecasting service returned HTTP 500");
      assertThat(ex.getMessage())
          .doesNotContain("2026-10-11")
          .doesNotContain("booked_covers")
          .doesNotContain("100.0");
    } finally {
      server.stop(0);
    }
  }

  private static ForecastModelHttpClient client(String baseUrl) {
    return new ForecastModelHttpClient(new TimesFmProperties(false, baseUrl), MAPPER);
  }

  private static ForecastModelRequest request() {
    return new ForecastModelRequest(
        List.of(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12)),
        List.of(new BigDecimal("100.0"), new BigDecimal("110.0")),
        List.of(new BigDecimal("50")),
        List.of(new BigDecimal("40")),
        List.of(new BigDecimal("45.0")),
        List.of(new BigDecimal("55.0")),
        2,
        List.of("0.1", "0.5", "0.9"));
  }
}
