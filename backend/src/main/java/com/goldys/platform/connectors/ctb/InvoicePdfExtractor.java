package com.goldys.platform.connectors.ctb;

/** Port every invoice-PDF parser implements; deterministic now, LLM fallback later (spec §6). */
public interface InvoicePdfExtractor {
  PdfExtractedInvoice extract(String text);
}
