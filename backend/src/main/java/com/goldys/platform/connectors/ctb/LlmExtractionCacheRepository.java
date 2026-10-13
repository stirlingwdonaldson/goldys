package com.goldys.platform.connectors.ctb;

import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by {@code text_sha256} (the SHA-256 of the input invoice text). */
public interface LlmExtractionCacheRepository extends JpaRepository<LlmExtractionCache, String> {}
