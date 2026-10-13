package com.goldys.platform.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * JDK {@link HttpClient} adapter for the self-hosted embedding + clustering service.
 *
 * <p>The service is data-agnostic: {@code POST {baseUrl}/embed} takes {@code {texts:[…]}} and
 * returns {@code {vectors:[[…]]}}, and {@code POST {baseUrl}/cluster} takes {@code {vectors:[[…]]}}
 * and returns {@code {nodes:[…], labels:[…], projection:[…]}}. The bodies are assembled explicitly
 * (rather than serialising the records directly) so the wire keys stay {@code texts} and {@code
 * vectors}.
 *
 * <p>Failures — non-2xx, IO, or an unparseable body — surface as {@link EmbeddingException} with an
 * operator-facing message that never carries the request payload or credentials.
 */
@Component
public class EmbeddingServiceHttpClient implements EmbeddingClient {

  private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

  private final String baseUrl;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public EmbeddingServiceHttpClient(EmbeddingProperties properties, ObjectMapper mapper) {
    this.baseUrl = properties.baseUrl();
    this.mapper = mapper;
    // Pin HTTP/1.1 for the same reason as TimesFmHttpClient: the default builder sends HTTP/2
    // cleartext upgrade headers that the FastAPI/uvicorn service behind this port does not accept.
    this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  }

  @Override
  public List<float[]> embed(List<String> texts) {
    ObjectNode body = mapper.createObjectNode();
    body.set("texts", mapper.valueToTree(texts));

    JsonNode response = post("/embed", body);
    JsonNode vectors = response.get("vectors");
    if (vectors == null || !vectors.isArray()) {
      throw new EmbeddingException("Embedding service returned an unparseable response");
    }
    List<float[]> result = new ArrayList<>(vectors.size());
    for (JsonNode vector : vectors) {
      float[] floats = new float[vector.size()];
      for (int i = 0; i < vector.size(); i++) {
        floats[i] = vector.get(i).floatValue();
      }
      result.add(floats);
    }
    return result;
  }

  @Override
  public ClusterResult cluster(List<float[]> vectors) {
    ObjectNode body = mapper.createObjectNode();
    body.set("vectors", mapper.valueToTree(vectors));

    JsonNode response = post("/cluster", body);
    JsonNode nodesJson = response.get("nodes");
    JsonNode labelsJson = response.get("labels");
    JsonNode projectionJson = response.get("projection");
    if (nodesJson == null
        || !nodesJson.isArray()
        || labelsJson == null
        || !labelsJson.isArray()
        || projectionJson == null
        || !projectionJson.isArray()) {
      throw new EmbeddingException("Embedding service returned an unparseable response");
    }

    List<ClusterNode> nodes = new ArrayList<>(nodesJson.size());
    for (JsonNode node : nodesJson) {
      int id = node.get("id").asInt();
      // The root's parentId is JSON null, and internal nodes carry a null productIndex; both map
      // to the boxed-null components of ClusterNode.
      Integer parentId = isNull(node.get("parentId")) ? null : node.get("parentId").asInt();
      boolean isLeaf = node.get("isLeaf").asBoolean();
      Integer productIndex =
          isNull(node.get("productIndex")) ? null : node.get("productIndex").asInt();
      double height = node.get("height").asDouble();
      int size = node.get("size").asInt();
      nodes.add(new ClusterNode(id, parentId, isLeaf, productIndex, height, size));
    }

    List<Integer> labels = new ArrayList<>(labelsJson.size());
    for (JsonNode label : labelsJson) {
      labels.add(label.asInt());
    }

    List<Point2d> projection = new ArrayList<>(projectionJson.size());
    for (JsonNode point : projectionJson) {
      projection.add(new Point2d(point.get("x").asDouble(), point.get("y").asDouble()));
    }

    return new ClusterResult(nodes, labels, projection);
  }

  private JsonNode post(String path, ObjectNode body) {
    String json;
    try {
      json = mapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new EmbeddingException("Failed to build the embedding request", e);
    }

    HttpRequest httpRequest =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(READ_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

    HttpResponse<String> response;
    try {
      response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    } catch (java.io.IOException e) {
      throw new EmbeddingException("Embedding service request failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EmbeddingException("Interrupted while calling the embedding service", e);
    }

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new EmbeddingException("Embedding service returned HTTP " + response.statusCode());
    }

    try {
      return mapper.readTree(response.body());
    } catch (JsonProcessingException e) {
      throw new EmbeddingException("Embedding service returned an unparseable response", e);
    }
  }

  private static boolean isNull(JsonNode node) {
    return node == null || node.isNull();
  }
}
