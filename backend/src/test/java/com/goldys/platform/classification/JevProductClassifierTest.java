package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Jev adapter against the real TypeSafe {@code Choice} schema confirmed at
 * https://docs.typesafe.ai/primitives/choice: the request is a {@code systemone} evaluation with a
 * {@code state}, {@code model}, and one {@code choice} question; the answer is nested under {@code
 * answers.<questionId>} and carries {@code choice}, {@code probabilities}, and {@code confidence}
 * keyed by option name (the category name). The adapter maps those names back to category ids.
 */
class JevProductClassifierTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final UUID BEVERAGES = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SNACKS = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void maps_choice_to_classification() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedAuth = new AtomicReference<>();

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/systemone",
        exchange -> {
          capturedBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          String response =
              "{\"model\":\"jev-1.13.0\","
                  + "\"answers\":{\"category\":{"
                  + "\"type\":\"choice\","
                  + "\"choice\":\"Beverages\","
                  + "\"confidence\":0.81,"
                  + "\"probabilities\":{\"Beverages\":0.8,\"Snacks\":0.2}}},"
                  + "\"usage\":{\"input_tokens\":318,\"output_tokens\":34}}";
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      JevProductClassifier classifier =
          new JevProductClassifier(
              new JevProperties(
                  false, "http://localhost:" + server.getAddress().getPort(), "test-api-key"),
              MAPPER);

      ProductClassification result = classifier.classify(request());

      assertThat(result.categoryId()).isEqualTo(BEVERAGES);
      assertThat(result.probabilities()).containsOnlyKeys(BEVERAGES, SNACKS);
      assertThat(result.probabilities().get(BEVERAGES)).isEqualByComparingTo("0.8");
      assertThat(result.probabilities().get(SNACKS)).isEqualByComparingTo("0.2");
      assertThat(result.confidence()).isEqualByComparingTo("0.81");

      // The request must follow the documented systemone Choice shape, and the option keys are the
      // category names (so the model sees readable labels), not the raw UUIDs.
      JsonNode body = MAPPER.readTree(capturedBody.get());
      assertThat(body.get("state").asText()).isEqualTo("Chicken parma and chips");
      assertThat(body.get("model").asText()).isEqualTo("jev-latest");
      JsonNode question = body.get("questions").get("category");
      assertThat(question.get("type").asText()).isEqualTo("choice");
      assertThat(question.get("instructions").asText()).isNotBlank();
      assertThat(question.get("criteria").get("Beverages").asText()).isEqualTo("Drinks");
      assertThat(question.get("criteria").get("Snacks").asText()).isEqualTo("Food to snack on");
      assertThat(capturedAuth.get()).isEqualTo("Bearer test-api-key");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void non_2xx_throws_jev_exception() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/systemone",
        exchange -> {
          byte[] response = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(500, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      JevProductClassifier classifier =
          new JevProductClassifier(
              new JevProperties(
                  false, "http://localhost:" + server.getAddress().getPort(), "test-api-key"),
              MAPPER);

      JevException ex = (JevException) catchThrowable(() -> classifier.classify(request()));

      assertThat(ex).isNotNull();
      assertThat(ex.getMessage()).isEqualTo("Jev service returned HTTP 500");
      assertThat(ex.getMessage())
          .doesNotContain("boom")
          .doesNotContain("Chicken parma")
          .doesNotContain("test-api-key");
    } finally {
      server.stop(0);
    }
  }

  private static ProductClassificationRequest request() {
    return new ProductClassificationRequest(
        "Chicken parma and chips",
        List.of(
            new CategoryOption(BEVERAGES, "Beverages", "Drinks"),
            new CategoryOption(SNACKS, "Snacks", "Food to snack on")));
  }
}
