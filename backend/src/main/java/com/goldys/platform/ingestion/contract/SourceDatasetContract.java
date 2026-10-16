package com.goldys.platform.ingestion.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * A versioned, machine-readable source dataset contract: what a source dataset emits (its
 * normalized output), what it means, and how it maps to the canonical model. Loaded from a
 * checked-in JSON document; the raw source format is declared alongside the output schema so schema
 * drift can be detected without consulting the source.
 *
 * @param contractVersion the contract-document format version (not the dataset schema version)
 * @param sourceSystem the stable source identifier (e.g. "CTB")
 * @param dataset the dataset name (e.g. "invoice_csv")
 * @param fetcherIdentity the raw-ledger fetcher identity for this dataset
 * @param schemaVersion the dataset's own schema version (semver)
 * @param compatibilityPolicy the bump rule ("semver")
 * @param recordGrain a human-readable description of one record's grain
 * @param outputSchema the JSON Schema (draft 2020-12) of the normalized output record
 * @param requiredColumns raw-format columns that must be present (missing = schema failure)
 * @param knownColumns every raw-format column the source may emit (unknown = additive drift)
 * @param metadata the business-semantics block (fields, keys, relationships, completeness, mapping,
 *     unmapped) that JSON Schema alone cannot express
 */
public record SourceDatasetContract(
    int contractVersion,
    String sourceSystem,
    String dataset,
    String fetcherIdentity,
    String schemaVersion,
    String compatibilityPolicy,
    String recordGrain,
    JsonNode outputSchema,
    List<String> requiredColumns,
    List<String> knownColumns,
    JsonNode metadata) {

  public SourceDatasetContract {
    requiredColumns = requiredColumns == null ? List.of() : List.copyOf(requiredColumns);
    knownColumns = knownColumns == null ? List.of() : List.copyOf(knownColumns);
  }
}
