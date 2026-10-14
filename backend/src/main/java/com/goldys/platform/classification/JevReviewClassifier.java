package com.goldys.platform.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * JDK {@link HttpClient} adapter for the hosted TypeSafe Jev "System One" service, which classifies
 * a guest review into an overall sentiment, a predicted 1..5 rating, and per-aspect sentiments.
 *
 * <p>The request follows the System One envelope (https://docs.typesafe.ai): {@code POST
 * {baseUrl}/v1/systemone} with a {@code state} (the review's feature text), a {@code model}, and a
 * {@code questions} map — one {@code choice} question for the overall sentiment, one {@code score}
 * question for the predicted rating, and one {@code choice} question per aspect to detect. The
 * response maps the fixed classification shape {@code {"sentiment":{"label","confidence"},
 * "predictedRating","aspects":[{"aspect","label","confidence"}]}} to {@link ReviewClassification};
 * an omitted {@code predictedRating} leaves the rating null (a review with no rating signal).
 *
 * <p>The exact Jev wire schema is an external unknown: the port and value types are fixed, only
 * this adapter's JSON mapping follows the Jev docs, so a schema correction is a one-file change.
 *
 * <p>Failures — non-2xx, IO, or an unparseable body — surface as {@link JevException} with an
 * operator-facing message that never carries the request payload or the API key.
 */
@Component
public class JevReviewClassifier implements ReviewClassifier {

  private static final Duration READ_TIMEOUT = Duration.ofMinutes(2);

  private static final String MODEL = "jev-latest";
  private static final String SENTIMENT_QUESTION = "sentiment";
  private static final String RATING_QUESTION = "rating";
  private static final String SENTIMENT_INSTRUCTIONS =
      "What is the overall sentiment of this review?";
  private static final String RATING_INSTRUCTIONS =
      "What star rating from 1 to 5 would this review warrant?";
  private static final String ASPECT_INSTRUCTIONS_PREFIX =
      "What is the sentiment of this review towards the \"";

  // Readable level/option descriptions so the model judges against situations, not bare labels.
  private static final String POSITIVE_DESCRIPTION =
      "The reviewer is positive: satisfied or pleased";
  private static final String NEUTRAL_DESCRIPTION =
      "The reviewer is neutral or mixed: neither clearly positive nor negative";
  private static final String NEGATIVE_DESCRIPTION =
      "The reviewer is negative: dissatisfied or critical";
  private static final List<String> RATING_LEVELS =
      List.of(
          "1 star: a very poor experience",
          "2 stars: a poor experience",
          "3 stars: an average experience",
          "4 stars: a good experience",
          "5 stars: an excellent experience");

  private final String baseUrl;
  private final String apiKey;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public JevReviewClassifier(JevProperties properties, ObjectMapper mapper) {
    this.baseUrl = properties.baseUrl();
    this.apiKey = properties.apiKey();
    this.mapper = mapper;
    // Pin HTTP/1.1 for the same reason as TimesFmHttpClient: the default builder sends HTTP/2
    // cleartext upgrade headers that some upstream front proxies reject.
    this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  }

  @Override
  public ReviewClassification classify(ReviewClassificationRequest request) {
    JsonNode response = post("/v1/systemone", buildBody(request));
    return map(response);
  }

  private ObjectNode buildBody(ReviewClassificationRequest request) {
    ObjectNode questions = mapper.createObjectNode();
    questions.set(SENTIMENT_QUESTION, sentimentChoice(SENTIMENT_INSTRUCTIONS));
    questions.set(RATING_QUESTION, ratingScore());
    for (String aspect : request.aspects()) {
      questions.set(aspect, sentimentChoice(ASPECT_INSTRUCTIONS_PREFIX + aspect + "\" aspect?"));
    }

    ObjectNode body = mapper.createObjectNode();
    body.put("state", request.featureText());
    body.put("model", MODEL);
    body.set("questions", questions);
    return body;
  }

  private ObjectNode sentimentChoice(String instructions) {
    ObjectNode criteria = mapper.createObjectNode();
    criteria.put("POSITIVE", POSITIVE_DESCRIPTION);
    criteria.put("NEUTRAL", NEUTRAL_DESCRIPTION);
    criteria.put("NEGATIVE", NEGATIVE_DESCRIPTION);

    ObjectNode question = mapper.createObjectNode();
    question.put("type", "choice");
    question.put("instructions", instructions);
    question.set("criteria", criteria);
    return question;
  }

  private ObjectNode ratingScore() {
    ArrayNode criteria = mapper.createArrayNode();
    for (String level : RATING_LEVELS) {
      criteria.add(level);
    }

    ObjectNode question = mapper.createObjectNode();
    question.put("type", "score");
    question.put("instructions", RATING_INSTRUCTIONS);
    question.set("criteria", criteria);
    return question;
  }

  private ReviewClassification map(JsonNode response) {
    Sentiment sentiment = sentiment(response.path("sentiment"));

    JsonNode ratingNode = response.get("predictedRating");
    BigDecimal predictedRating =
        (ratingNode == null || ratingNode.isNull()) ? null : ratingNode.decimalValue();

    List<AspectSentiment> aspects = new ArrayList<>();
    JsonNode aspectsNode = response.path("aspects");
    if (aspectsNode.isArray()) {
      for (JsonNode aspectNode : aspectsNode) {
        aspects.add(aspectSentiment(aspectNode));
      }
    }

    return new ReviewClassification(sentiment, predictedRating, aspects);
  }

  private Sentiment sentiment(JsonNode node) {
    String label = node.path("label").asText(null);
    JsonNode confidence = node.get("confidence");
    if (label == null || confidence == null || !confidence.isNumber()) {
      throw new JevException("Jev service returned an unparseable response");
    }
    return new Sentiment(label, confidence.decimalValue());
  }

  private AspectSentiment aspectSentiment(JsonNode node) {
    String aspect = node.path("aspect").asText(null);
    String label = node.path("label").asText(null);
    JsonNode confidence = node.get("confidence");
    if (aspect == null || label == null || confidence == null || !confidence.isNumber()) {
      throw new JevException("Jev service returned an unparseable response");
    }
    return new AspectSentiment(aspect, label, confidence.decimalValue());
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
