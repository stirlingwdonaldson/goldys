package com.goldys.platform.ingestion.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a source dataset contract from a checked-in JSON document on the classpath. Contracts are
 * versioned in git, so a contract the code depends on is always reproducible from the same commit
 * as the code.
 */
public class ContractLoader {
  private final ObjectMapper mapper;

  public ContractLoader() {
    this(new ObjectMapper());
  }

  public ContractLoader(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public SourceDatasetContract load(String classpathResource) {
    try (InputStream in = contractStream(classpathResource)) {
      JsonNode root = mapper.readTree(in);
      JsonNode source = root.get("source");
      JsonNode compatibility = root.get("compatibility");
      JsonNode rawFormat = root.get("rawFormat");
      return new SourceDatasetContract(
          root.path("contractVersion").asInt(),
          source.path("sourceSystem").asText(),
          source.path("dataset").asText(),
          source.path("fetcherIdentity").asText(),
          root.path("schemaVersion").asText(),
          compatibility.path("policy").asText(),
          root.path("recordGrain").asText(),
          root.get("outputSchema"),
          stringList(rawFormat.get("requiredColumns")),
          stringList(rawFormat.get("knownColumns")),
          root.get("fields"));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load source contract: " + classpathResource, e);
    }
  }

  private static InputStream contractStream(String resource) {
    InputStream in = ContractLoader.class.getClassLoader().getResourceAsStream(resource);
    if (in == null) {
      throw new IllegalStateException("Source contract not on classpath: " + resource);
    }
    return in;
  }

  private static List<String> stringList(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (JsonNode n : node) {
      out.add(n.asText());
    }
    return out;
  }
}
