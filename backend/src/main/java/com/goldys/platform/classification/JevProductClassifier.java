package com.goldys.platform.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * JDK {@link HttpClient} adapter for the hosted TypeSafe Jev "System One" service, which assigns a
 * product to one candidate category via a single {@code Choice} question.
 *
 * <p>The wire schema follows https://docs.typesafe.ai/primitives/choice: {@code POST
 * {baseUrl}/v1/systemone} with a {@code state} (the product's feature text), a {@code model}, and a
 * {@code questions} map holding one {@code choice} question whose {@code criteria} are the category
 * names (the model sees readable labels) mapped to their descriptions. The answer comes back under
 * {@code answers.<questionId>} with a {@code choice} name, a {@code probabilities} map keyed by
 * category name, and a {@code confidence}. This adapter maps those names back to the category ids
 * supplied in the request, relying on the globally-unique {@code product_category.name} invariant.
 *
 * <p>Failures — non-2xx, IO, or an unparseable/unexpected body — surface as {@link JevException}
 * with an operator-facing message that never carries the request payload or the API key.
 */
@Component
public class JevProductClassifier implements ProductClassifier {

  private static final Duration READ_TIMEOUT = Duration.ofMinutes(2);

  private static final String MODEL = "jev-latest";
  private static final String QUESTION_ID = "category";
  private static final String INSTRUCTIONS = "Which category does this product belong to?";

  private final String baseUrl;
  private final String apiKey;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public JevProductClassifier(JevProperties properties, ObjectMapper mapper) {
    this.baseUrl = properties.baseUrl();
    this.apiKey = properties.apiKey();
    this.mapper = mapper;
    // Pin HTTP/1.1 for the same reason as ForecastModelHttpClient: the default builder sends
    // HTTP/2 cleartext upgrade headers that some upstream front proxies reject.
    this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  }

  @Override
  public ProductClassification classify(ProductClassificationRequest request) {
    Map<String, UUID> categoryIdByName = new HashMap<>();
    ObjectNode criteria = mapper.createObjectNode();
    for (CategoryOption option : request.options()) {
      categoryIdByName.put(option.name(), option.categoryId());
      // A description is the best rubric for the model; fall back to the name when absent.
      String description =
          (option.description() == null || option.description().isBlank())
              ? option.name()
              : option.description();
      criteria.put(option.name(), description);
    }

    ObjectNode question = mapper.createObjectNode();
    question.put("type", "choice");
    question.put("instructions", INSTRUCTIONS);
    question.set("criteria", criteria);
    ObjectNode questions = mapper.createObjectNode();
    questions.set(QUESTION_ID, question);

    ObjectNode body = mapper.createObjectNode();
    body.put("state", request.featureText());
    body.put("model", MODEL);
    body.set("questions", questions);

    JsonNode response = post("/v1/systemone", body);
    JsonNode answer = response.path("answers").path(QUESTION_ID);
    if (answer.isMissingNode()) {
      throw new JevException("Jev service returned an unparseable response");
    }

    String choice = answer.path("choice").asText(null);
    JsonNode probabilities = answer.path("probabilities");
    JsonNode confidence = answer.get("confidence");
    if (choice == null
        || !probabilities.isObject()
        || confidence == null
        || !confidence.isNumber()) {
      throw new JevException("Jev service returned an unparseable response");
    }

    UUID categoryId = categoryIdByName.get(choice);
    if (categoryId == null) {
      throw new JevException("Jev service returned an unexpected category");
    }

    Map<UUID, BigDecimal> byId = new HashMap<>();
    for (Map.Entry<String, JsonNode> entry : probabilities.properties()) {
      UUID id = categoryIdByName.get(entry.getKey());
      if (id == null) {
        throw new JevException("Jev service returned an unexpected category");
      }
      byId.put(id, entry.getValue().decimalValue());
    }

    return new ProductClassification(categoryId, byId, confidence.decimalValue());
  }

  private JsonNode post(String path, ObjectNode body) {
    String json;
    try {
      json = mapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new JevException("Failed to build the Jev request", e);
    }

    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(READ_TIMEOUT);
    if (apiKey != null && !apiKey.isBlank()) {
      builder.header("Authorization", "Bearer " + apiKey);
    }
    HttpRequest httpRequest = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();

    HttpResponse<String> response;
    try {
      response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    } catch (java.io.IOException e) {
      throw new JevException("Jev service request failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JevException("Interrupted while calling the Jev service", e);
    }

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new JevException("Jev service returned HTTP " + response.statusCode());
    }

    try {
      return mapper.readTree(response.body());
    } catch (JsonProcessingException e) {
      throw new JevException("Jev service returned an unparseable response", e);
    }
  }
}
