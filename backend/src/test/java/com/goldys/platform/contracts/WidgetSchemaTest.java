package com.goldys.platform.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WidgetSchemaTest {
  @Test
  void schemaHasAClosedVersionedRootContract() throws Exception {
    JsonNode schema =
        new ObjectMapper()
            .readTree(
                Files.readString(Path.of("..", "docs", "contracts", "widget-spec.schema.json")));

    assertThat(schema.get("$id").asText())
        .isEqualTo("https://goldys.local/schemas/widget-spec-v1.json");
    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required"))
        .extracting(JsonNode::asText)
        .containsExactly("version", "type", "title", "data");
  }
}
