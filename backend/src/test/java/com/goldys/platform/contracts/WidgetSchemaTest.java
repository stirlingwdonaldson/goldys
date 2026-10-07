package com.goldys.platform.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WidgetSchemaTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void widgetSchemaIsAVersionedDiscriminatedContract() throws Exception {
    JsonNode schema = read("widget-spec.schema.json");

    assertThat(schema.get("$id").asText())
        .isEqualTo("https://goldys.local/schemas/widget-spec-v2.json");
    assertThat(schema.get("discriminator").get("propertyName").asText()).isEqualTo("type");
    assertThat(schema.get("oneOf")).hasSize(5);
    assertThat(
            schema
                .path("$defs")
                .path("base")
                .path("properties")
                .path("schemaVersion")
                .get("const")
                .asInt())
        .isEqualTo(2);
  }

  @Test
  void dashboardDocumentSchemaIsV2ClosedAndVersioned() throws Exception {
    JsonNode schema = read("dashboard-document.schema.json");

    assertThat(schema.at("/$id").asText())
        .isEqualTo("https://goldys.local/schemas/dashboard-document-v2.json");
    assertThat(schema.at("/properties/schemaVersion/const").asInt()).isEqualTo(2);
    assertThat(schema.at("/additionalProperties").asBoolean()).isFalse();
    // new fields exist
    assertThat(schema.at("/required").toString()).contains("visibility", "filters");
  }

  private static JsonNode read(String name) throws Exception {
    return MAPPER.readTree(Files.readString(Path.of("..", "docs", "contracts", name)));
  }
}
