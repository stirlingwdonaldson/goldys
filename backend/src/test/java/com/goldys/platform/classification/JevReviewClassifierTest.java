package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Jev review-classification adapter against the real System One response shape: each
 * answer comes back under {@code answers.<questionId>} — a {@code choice} answer with {@code
 * choice} + {@code confidence}, and the {@code score} rating answer with a 0-based {@code score}
 * (https://docs.typesafe.ai).
 */
class JevReviewClassifierTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String RESPONSE =
      "{"
          + "\"answers\":{"
          + "\"sentiment\":{\"type\":\"choice\",\"choice\":\"NEGATIVE\",\"confidence\":0.94,"
          + "\"probabilities\":{\"POSITIVE\":0.02,\"NEUTRAL\":0.04,\"NEGATIVE\":0.94}},"
          + "\"rating\":{\"type\":\"score\",\"score\":0.8,\"confidence\":0.70,"
          + "\"probabilities\":{\"0\":0.2,\"1\":0.8,\"2\":0.0,\"3\":0.0,\"4\":0.0}},"
          + "\"food\":{\"type\":\"choice\",\"choice\":\"NEGATIVE\",\"confidence\":0.71},"
          + "\"food_mentioned\":{\"type\":\"noul\",\"noul\":0.9},"
          + "\"service\":{\"type\":\"choice\",\"choice\":\"NEGATIVE\",\"confidence\":0.97},"
          + "\"service_mentioned\":{\"type\":\"noul\",\"noul\":0.95},"
          + "\"ambience\":{\"type\":\"choice\",\"choice\":\"NEUTRAL\",\"confidence\":0.62},"
          + "\"ambience_mentioned\":{\"type\":\"noul\",\"noul\":0.1},"
          + "\"value\":{\"type\":\"choice\",\"choice\":\"POSITIVE\",\"confidence\":0.55},"
          + "\"value_mentioned\":{\"type\":\"noul\",\"noul\":0.2},"
          + "\"noise\":{\"type\":\"choice\",\"choice\":\"NEUTRAL\",\"confidence\":0.80},"
          + "\"noise_mentioned\":{\"type\":\"noul\",\"noul\":0.05},"
          + "\"wait_time\":{\"type\":\"choice\",\"choice\":\"NEGATIVE\",\"confidence\":0.66},"
          + "\"wait_time_mentioned\":{\"type\":\"noul\",\"noul\":0.85}"
          + "},"
          + "\"usage\":{\"input_tokens\":300,\"output_tokens\":40}"
          + "}";

  @Test
  void maps_jev_response_to_review_classification() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedAuth = new AtomicReference<>();

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/systemone",
        exchange -> {
          capturedBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] bytes = RESPONSE.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      JevReviewClassifier classifier =
          new JevReviewClassifier(
              new JevProperties(
                  true, "http://localhost:" + server.getAddress().getPort(), "test-api-key"),
              MAPPER);

      ReviewClassification result = classifier.classify(request());

      assertThat(result.sentiment().label()).isEqualTo("NEGATIVE");
      assertThat(result.sentiment().confidence()).isEqualByComparingTo("0.94");
      // The rating is a 0-based Jev score over the five star levels, shifted onto 1..5.
      assertThat(result.predictedRating()).isEqualByComparingTo("1.8");

      // Sparse aspects: ambience, value and noise fall below the 0.5 presence threshold and are
      // dropped even though their sentiment was also asked.
      assertThat(result.aspects()).hasSize(3);
      assertThat(result.aspects())
          .extracting(AspectSentiment::aspect)
          .containsExactly("food", "service", "wait_time");
      assertThat(result.aspects())
          .extracting(AspectSentiment::label)
          .containsExactly("NEGATIVE", "NEGATIVE", "NEGATIVE");
      assertThat(result.aspects().get(1).confidence()).isEqualByComparingTo("0.97");

      // The request must follow the System One envelope: the state is the feature text and the
      // model is the Jev model id; the API key travels as a bearer token.
      JsonNode body = MAPPER.readTree(capturedBody.get());
      assertThat(body.get("state").asText())
          .isEqualTo("Disinterested wait staff. Plonked food down");
      assertThat(body.get("model").asText()).isEqualTo("jev-latest");
      assertThat(body.path("questions")).hasSize(14); // sentiment + rating + 6 aspects + 6 presence
      assertThat(body.path("questions").path("rating").path("type").asText()).isEqualTo("score");
      assertThat(body.path("questions").path("food").path("type").asText()).isEqualTo("choice");
      assertThat(body.path("questions").path("food_mentioned").path("type").asText())
          .isEqualTo("noul");
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
      JevReviewClassifier classifier =
          new JevReviewClassifier(
              new JevProperties(
                  true, "http://localhost:" + server.getAddress().getPort(), "test-api-key"),
              MAPPER);

      JevException ex = (JevException) catchThrowable(() -> classifier.classify(request()));

      assertThat(ex).isNotNull();
      assertThat(ex.getMessage()).isEqualTo("Jev service returned HTTP 500");
      assertThat(ex.getMessage())
          .doesNotContain("boom")
          .doesNotContain("Disinterested")
          .doesNotContain("test-api-key");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void response_without_answers_throws_unparseable() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/systemone",
        exchange -> {
          byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      JevReviewClassifier classifier =
          new JevReviewClassifier(
              new JevProperties(
                  true, "http://localhost:" + server.getAddress().getPort(), "test-api-key"),
              MAPPER);

      JevException ex = (JevException) catchThrowable(() -> classifier.classify(request()));

      assertThat(ex).isNotNull();
      assertThat(ex.getMessage()).isEqualTo("Jev service returned an unparseable response");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void disabled_classifier_refuses_without_any_request() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/systemone",
        exchange -> {
          requests.incrementAndGet();
          byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      JevReviewClassifier classifier =
          new JevReviewClassifier(
              new JevProperties(
                  false, "http://localhost:" + server.getAddress().getPort(), "test-api-key"),
              MAPPER);

      JevException ex = (JevException) catchThrowable(() -> classifier.classify(request()));

      assertThat(ex).isNotNull();
      assertThat(ex.getMessage()).contains("CLASSIFICATION_JEV_ENABLED=true");
      assertThat(ex.getMessage()).doesNotContain("test-api-key").doesNotContain("Disinterested");
      assertThat(requests.get()).isZero();
    } finally {
      server.stop(0);
    }
  }

  private static ReviewClassificationRequest request() {
    return new ReviewClassificationRequest(
        "Disinterested wait staff. Plonked food down",
        List.of("food", "service", "ambience", "value", "noise", "wait_time"));
  }
}
