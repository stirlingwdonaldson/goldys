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
 * response returns each answer under {@code answers.<questionId>}: a {@code choice} answer carries
 * {@code choice} + {@code confidence}, and the {@code score} rating answer carries a 0-based {@code
 * score} that this adapter shifts onto the 1..5 star scale.
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

  private final boolean enabled;
  private final String baseUrl;
  private final String apiKey;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public JevReviewClassifier(JevProperties properties, ObjectMapper mapper) {
    this.enabled = properties.enabled();
    this.baseUrl = properties.baseUrl();
    this.apiKey = properties.apiKey();
    this.mapper = mapper;
    // Pin HTTP/1.1 for the same reason as TimesFmHttpClient: the default builder sends HTTP/2
    // cleartext upgrade headers that some upstream front proxies reject.
    this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  }

  @Override
  public ReviewClassification classify(ReviewClassificationRequest request) {
    // Defensive backstop: the orchestrator skips classification when Jev is disabled, but a direct
    // adapter caller must never reach the network either. Refuse before any request is built/sent.
    if (!enabled) {
      throw new JevException(
          "Review Jev classification is disabled; set CLASSIFICATION_JEV_ENABLED=true and"
              + " CLASSIFICATION_JEV_API_KEY");
    }
    JsonNode response = post("/v1/systemone", buildBody(request));
    return map(response, request.aspects());
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

  private ReviewClassification map(JsonNode response, List<String> aspects) {
    JsonNode answers = response.path("answers");
    if (answers.isMissingNode() || !answers.isObject()) {
      throw new JevException("Jev service returned an unparseable response");
    }

    Sentiment sentiment = choiceSentiment(answers.path(SENTIMENT_QUESTION));
    BigDecimal predictedRating = ratingScore(answers.path(RATING_QUESTION));

    List<AspectSentiment> aspectList = new ArrayList<>(aspects.size());
    for (String aspect : aspects) {
      aspectList.add(choiceAspect(aspect, answers.path(aspect)));
    }

    return new ReviewClassification(sentiment, predictedRating, aspectList);
  }

  /** A {@code choice} answer's selected label + confidence (the sentiment and aspect questions). */
  private Sentiment choiceSentiment(JsonNode answer) {
    String label = answer.path("choice").asText(null);
    JsonNode confidence = answer.get("confidence");
    if (label == null || confidence == null || !confidence.isNumber()) {
      throw new JevException("Jev service returned an unparseable response");
    }
    return new Sentiment(label, confidence.decimalValue());
  }

  /** The {@code score} rating answer, shifted from Jev's 0-based level line onto the 1..5 scale. */
  private BigDecimal ratingScore(JsonNode answer) {
    JsonNode score = answer.get("score");
    if (score == null || !score.isNumber()) {
      throw new JevException("Jev service returned an unparseable response");
    }
    return score.decimalValue().add(BigDecimal.ONE);
  }

  /** One aspect question's answer; the aspect name is the question id. */
  private AspectSentiment choiceAspect(String aspect, JsonNode answer) {
    Sentiment sentiment = choiceSentiment(answer);
    return new AspectSentiment(aspect, sentiment.label(), sentiment.confidence());
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
