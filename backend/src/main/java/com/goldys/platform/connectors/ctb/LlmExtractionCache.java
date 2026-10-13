package com.goldys.platform.connectors.ctb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One cached LLM invoice extraction, keyed by the SHA-256 of the input invoice text. Storing the
 * extraction makes the re-enrich replay deterministic: the same text always maps to the same
 * extracted invoice, instead of re-calling a probabilistic model that returns slightly different
 * lines each time.
 */
@Entity
@Table(name = "llm_extraction_cache")
class LlmExtractionCache {

  @Id
  @Column(name = "text_sha256", nullable = false, length = 64)
  private String textSha256;

  @Column(name = "extracted_json", nullable = false)
  private String extractedJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected LlmExtractionCache() {}

  private LlmExtractionCache(String textSha256, String extractedJson, Instant createdAt) {
    this.textSha256 = Objects.requireNonNull(textSha256, "textSha256");
    this.extractedJson = Objects.requireNonNull(extractedJson, "extractedJson");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  static LlmExtractionCache create(String textSha256, String extractedJson, Instant createdAt) {
    return new LlmExtractionCache(textSha256, extractedJson, createdAt);
  }

  String extractedJson() {
    return extractedJson;
  }
}
