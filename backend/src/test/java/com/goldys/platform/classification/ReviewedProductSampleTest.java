package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Replay the actual approved 3 November Jev responses; never call a paid model in CI. */
class ReviewedProductSampleTest {
  @Test
  void real_sample_responses_validate_and_uncertainty_is_preserved() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    Path evidence = Path.of("../docs/audits/product-classification-2026-11-03/evidence");
    JsonNode sample =
        mapper.readTree(Files.readString(evidence.resolve("evaluation-results.json")));
    JsonNode criteria =
        mapper.readTree(Files.readString(evidence.resolve("evaluation-criteria.json")));
    List<CategoryOption> options = new ArrayList<>();
    criteria
        .properties()
        .forEach(
            e -> {
              if (!e.getKey().equals("Unclassified"))
                options.add(
                    new CategoryOption(
                        UUID.nameUUIDFromBytes(e.getKey().getBytes(StandardCharsets.UTF_8)),
                        e.getKey(),
                        e.getValue().asText()));
            });
    AtomicReference<JsonNode> response = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/systemone",
        exchange -> {
          byte[] bytes = mapper.writeValueAsBytes(response.get());
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var classifier =
          new JevProductClassifier(
              new JevProperties(false, "http://localhost:" + server.getAddress().getPort(), "test"),
              mapper);
      int holds = 0, evaluated = 0, agreed = 0, abstained = 0;
      for (JsonNode row : sample) {
        if (row.path("held").asBoolean()) {
          holds++;
          continue;
        }
        response.set(row.get("response"));
        var result =
            classifier.classify(
                new ProductClassificationRequest(row.path("text").asText(), options));
        if (row.path("correct").asBoolean()) agreed++;
        if (row.path("observed").asText().equals("Unclassified")) {
          assertThat(result.categoryId()).isNull();
          abstained++;
        } else
          assertThat(result.categoryId())
              .isEqualTo(
                  UUID.nameUUIDFromBytes(
                      row.path("observed").asText().getBytes(StandardCharsets.UTF_8)));
        assertThat(result.responseModel()).isEqualTo("jev-1.14.0");
        evaluated++;
      }
      // Keep the original errors visible; this fixture is evidence, not an accuracy target.
      assertThat(evaluated).isEqualTo(21);
      assertThat(holds).isEqualTo(3);
      assertThat(agreed).isEqualTo(18);
      assertThat(abstained).isEqualTo(1);
    } finally {
      server.stop(0);
    }
  }
}
