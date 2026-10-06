package com.goldys.platform.ingestion.port;

/**
 * Extracts plain text from a source document, so the concrete extractor (e.g. PDFBox) is swappable
 * behind this boundary without touching line parsing or canonicalization.
 */
public interface DocumentTextExtractor {
  String extractText(byte[] document, String contentType);
}
