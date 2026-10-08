package com.goldys.platform.connectors.ctb;

import java.util.List;

/** An invoice's PDF text reduced to its enrichment-relevant lines. */
public record PdfExtractedInvoice(String invoiceNumber, List<PdfExtractedLine> lines) {}
