package com.goldys.platform.ingestion.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates a source dataset against its contract: the raw-format header (drift detection) and the
 * normalized output (JSON Schema). Validation never mutates the raw evidence — it only reports
 * which contract rules were violated, so a failure can be recorded as a pipeline-stage failure
 * rather than a silent success.
 */
public class ContractValidator {
  private final ContractLoader loader;
  private final java.util.Map<String, JsonSchema> schemas =
      new java.util.concurrent.ConcurrentHashMap<>();

  public ContractValidator() {
    this(new ContractLoader());
  }

  public ContractValidator(ContractLoader loader) {
    this.loader = loader;
  }

  /** The outcome of comparing an actual raw-format header against the contract's columns. */
  public record HeaderValidation(List<String> missingRequired, List<String> unknownColumns) {
    public boolean valid() {
      return missingRequired.isEmpty();
    }
  }

  /** Detects missing required columns and additive (unknown) columns in a raw header. */
  public HeaderValidation validateHeader(String resource, List<String> actualColumns) {
    SourceDatasetContract contract = loader.load(resource);
    Set<String> actual = new LinkedHashSet<>(actualColumns);
    List<String> missing = new ArrayList<>();
    for (String required : contract.requiredColumns()) {
      if (!actual.contains(required)) {
        missing.add(required);
      }
    }
    List<String> unknown = new ArrayList<>();
    for (String column : actual) {
      if (!contract.knownColumns().contains(column)) {
        unknown.add(column);
      }
    }
    return new HeaderValidation(missing, unknown);
  }

  /**
   * Validates a normalized output record against the contract's JSON Schema, returning the
   * violations (empty when valid). Compiled schemas are cached by contract document.
   */
  public List<String> validateOutput(String resource, JsonNode output) {
    JsonSchema schema =
        schemas.computeIfAbsent(
            resource,
            name ->
                JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(loader.load(name).outputSchema()));
    Set<ValidationMessage> errors = schema.validate(output);
    List<String> messages = new ArrayList<>();
    for (ValidationMessage error : errors) {
      messages.add(error.getInstanceLocation() + ": " + error.getMessage());
    }
    return messages;
  }
}
