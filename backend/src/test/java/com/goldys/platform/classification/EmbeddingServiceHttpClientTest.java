package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Exercises the embedding-service adapter against its wire contract: {@code POST {baseUrl}/embed}
 * takes {@code {texts:[…]}} and returns {@code {vectors:[[…]]}}; {@code POST {baseUrl}/cluster}
 * takes {@code {vectors:[[…]]}} and returns {@code {nodes:[…], labels:[…], projection:[…]}} where a
 * node's {@code parentId}/{@code productIndex} may be JSON {@code null}.
 */
class EmbeddingServiceHttpClientTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void embed_returns_vectors() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/embed",
        exchange -> {
          captured.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response = "{\"vectors\":[[0.1,0.2],[0.3,0.4]]}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      EmbeddingServiceHttpClient client =
          new EmbeddingServiceHttpClient(
              new EmbeddingProperties(false, "http://localhost:" + server.getAddress().getPort()),
              MAPPER);

      List<float[]> vectors = client.embed(List.of("a", "b"));

      assertThat(vectors).hasSize(2);
      assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f);
      assertThat(vectors.get(1)).containsExactly(0.3f, 0.4f);

      JsonNode body = MAPPER.readTree(captured.get());
      assertThat(body.get("texts").get(0).asText()).isEqualTo("a");
      assertThat(body.get("texts").get(1).asText()).isEqualTo("b");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void cluster_maps_null_parent_and_product_index() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/cluster",
        exchange -> {
          byte[] response =
              ("{\"nodes\":["
                      + "{\"id\":0,\"parentId\":2,\"isLeaf\":true,\"productIndex\":0,"
                      + "\"height\":0.0,\"size\":1},"
                      + "{\"id\":1,\"parentId\":2,\"isLeaf\":true,\"productIndex\":1,"
                      + "\"height\":0.0,\"size\":1},"
                      + "{\"id\":2,\"parentId\":null,\"isLeaf\":false,\"productIndex\":null,"
                      + "\"height\":1.5,\"size\":2}],"
                      + "\"labels\":[0,0],"
                      + "\"projection\":[{\"x\":1.0,\"y\":2.0},{\"x\":3.0,\"y\":4.0}]}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      EmbeddingServiceHttpClient client =
          new EmbeddingServiceHttpClient(
              new EmbeddingProperties(false, "http://localhost:" + server.getAddress().getPort()),
              MAPPER);

      ClusterResult result = client.cluster(List.of(new float[] {0.1f}, new float[] {0.2f}));

      assertThat(result.nodes()).hasSize(3);

      ClusterNode leaf0 = result.nodes().get(0);
      assertThat(leaf0.isLeaf()).isTrue();
      assertThat(leaf0.parentId()).isEqualTo(2);
      assertThat(leaf0.productIndex()).isEqualTo(0);
      assertThat(leaf0.height()).isEqualTo(0.0);
      assertThat(leaf0.size()).isEqualTo(1);

      ClusterNode leaf1 = result.nodes().get(1);
      assertThat(leaf1.parentId()).isEqualTo(2);
      assertThat(leaf1.productIndex()).isEqualTo(1);

      ClusterNode root = result.nodes().get(2);
      assertThat(root.isLeaf()).isFalse();
      assertThat(root.parentId()).isNull();
      assertThat(root.productIndex()).isNull();
      assertThat(root.height()).isEqualTo(1.5);
      assertThat(root.size()).isEqualTo(2);

      assertThat(result.labels()).containsExactly(0, 0);
      assertThat(result.projection()).containsExactly(new Point2d(1.0, 2.0), new Point2d(3.0, 4.0));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void cluster_non_2xx_throws_embedding_exception() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/cluster",
        exchange -> {
          byte[] response = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(500, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      EmbeddingServiceHttpClient client =
          new EmbeddingServiceHttpClient(
              new EmbeddingProperties(false, "http://localhost:" + server.getAddress().getPort()),
              MAPPER);

      EmbeddingException ex =
          (EmbeddingException) catchThrowable(() -> client.cluster(List.of(new float[] {0.1f})));

      assertThat(ex).isNotNull();
      assertThat(ex.getMessage()).isEqualTo("Embedding service returned HTTP 500");
      assertThat(ex.getMessage()).doesNotContain("boom");
    } finally {
      server.stop(0);
    }
  }
}
