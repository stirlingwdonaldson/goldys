package com.goldys.platform.connectors.ctb;

/**
 * A deterministic per-supplier invoice-PDF parser. Unlike the LLM fallback, these never invent
 * values: they either recognize their registered layout and return its lines, or return an empty
 * invoice so the hybrid extractor can fall through to the next template or to the LLM (spec §6).
 * The marker lets {@link HybridInvoicePdfExtractor} collect only the deterministic templates.
 */
public interface DeterministicPdfExtractor extends InvoicePdfExtractor {}
