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
 * Exercises the Jev review-classification adapter against the fixed response shape from the review
 * classification spec: {@code
 * {"sentiment":{"label","confidence"},"predictedRating","aspects":[{"aspect","label","confidence"}]}}.
 * The request is the Jev System One envelope ({@code state} + {@code model} + typed {@code
 * questions}), pinned in the adapter; a schema correction there is a one-file change.
 */
class JevReviewClassifierTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

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
          String response =
              "{\"sentiment\":{\"label\":\"NEGATIVE\",\"confidence\":0.94},"
                  + "\"predictedRating\":1.8,"
                  + "\"aspects\":["
                  + "{\"aspect\":\"service\",\"label\":\"NEGATIVE\",\"confidence\":0.97},"
                  + "{\"aspect\":\"food\",\"label\":\"NEGATIVE\",\"confidence\":0.71}]}";
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
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
      assertThat(result.predictedRating()).isEqualByComparingTo("1.8");
      assertThat(result.aspects()).hasSize(2);
      assertThat(result.aspects().get(0).aspect()).isEqualTo("service");
      assertThat(result.aspects().get(0).label()).isEqualTo("NEGATIVE");
      assertThat(result.aspects().get(0).confidence()).isEqualByComparingTo("0.97");
      assertThat(result.aspects().get(1).aspect()).isEqualTo("food");
      assertThat(result.aspects().get(1).label()).isEqualTo("NEGATIVE");
      assertThat(result.aspects().get(1).confidence()).isEqualByComparingTo("0.71");

      // The request must follow the System One envelope: the state is the feature text and the
      // model is the Jev model id; the API key travels as a bearer token.
      JsonNode body = MAPPER.readTree(capturedBody.get());
      assertThat(body.get("state").asText())
          .isEqualTo("Disinterested wait staff. Plonked food down");
      assertThat(body.get("model").asText()).isEqualTo("jev-latest");
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
  void null_predicted_rating_maps_to_null() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/systemone",
        exchange -> {
          String response =
              "{\"sentiment\":{\"label\":\"POSITIVE\",\"confidence\":0.88},"
                  + "\"aspects\":[{\"aspect\":\"service\",\"label\":\"POSITIVE\",\"confidence\":0.90}]}";
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
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

      assertThat(result.sentiment().label()).isEqualTo("POSITIVE");
      assertThat(result.predictedRating()).isNull();
      assertThat(result.aspects()).hasSize(1);
      assertThat(result.aspects().get(0).aspect()).isEqualTo("service");
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
