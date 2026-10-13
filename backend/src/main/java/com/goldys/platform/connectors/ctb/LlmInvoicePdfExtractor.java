package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * LLM-backed {@link InvoicePdfExtractor}: schema-prompts a {@link ChatModel} to reduce invoice PDF
 * text to the {@link PdfExtractedInvoice}/{@link PdfExtractedLine} JSON contract, then parses it
 * with Jackson. Used as the fallback for supplier layouts the deterministic templates do not
 * recognize.
 *
 * <p>The model is injected as an {@link ObjectProvider} because it is absent when {@code
 * spring.ai.model.chat=none} (the default). With no model available the extractor returns an empty
 * invoice (no error); a model that throws, or whose output cannot be parsed as the JSON contract,
 * is returned with a non-null {@link PdfExtractedInvoice#extractionError()} so the caller flags it
 * as {@link InvoiceIngestFlagType#LLM_EXTRACTION_FAILED} rather than mistaking a failure for a
 * genuine "no lines". The JSON contract is fixed and the temperature is forced to 0 so the fallback
 * is as deterministic as a probabilistic model can be.
 */
@Component
public class LlmInvoicePdfExtractor implements InvoicePdfExtractor {

  private static final Logger log = LoggerFactory.getLogger(LlmInvoicePdfExtractor.class);

  /**
   * The schema the model is forced to emit. The JSON keys match {@link PdfExtractedInvoice} and
   * {@link PdfExtractedLine} record component names so Jackson deserializes them directly. The
   * prompt insists on null (never a guess) for absent fields: a hallucinated UOM or WET would
   * silently corrupt unit-cost / liquor-tax accounting, so absence is the safe default.
   */
  private static final String SCHEMA_PROMPT =
      "You extract line items from an Australian supplier invoice (PDF text). "
          + "Return ONLY a single JSON object matching this exact schema, no prose, no markdown:\n"
          + "{\"invoiceNumber\": string|null, \"lines\": [{\"stockCode\": string|null, "
          + "\"description\": string|null, \"quantity\": number|null, \"uom\": string|null, "
          + "\"unitQuantity\": number|null, \"packSize\": number|null, \"wetAmount\": number|null, "
          + "\"amountExTax\": number|null}]}\n"
          + "Rules:\n"
          + "- invoiceNumber: the invoice number value printed on the invoice (e.g. 758262), "
          + "never a label such as 'Tax Invoice No'; null if absent.\n"
          + "- lines: purchased items, fees and charges only. Omit notes and annotations such as "
          + "'COMMENT' or '(2 BOXES)'.\n"
          + "- stockCode: the supplier's stock/code column value, or null if absent.\n"
          + "- description: the line item description.\n"
          + "- quantity: the shipped/invoiced quantity (numeric only).\n"
          + "- uom: the unit of measure (e.g. KG, EACH, CTN, LT) from the UNIT/UOM column, or null.\n"
          + "- unitQuantity: the per-pack unit count (e.g. 48 in '48 EACH'), or null.\n"
          + "- packSize: the pack/carton size (e.g. 4 in '4 CTN'), or null.\n"
          + "- wetAmount: the Wine Equalisation Tax amount for the line (liquor only), or null.\n"
          + "- amountExTax: the line total excluding tax, or null if the text does not state it.\n"
          + "Never invent a value. When a field is not present in the text, emit null for it.";

  private static final Clock CLOCK = Clock.systemUTC();

  private final ObjectProvider<ChatModel> chatModel;
  private final ObjectMapper mapper;
  private final LlmExtractionCacheRepository cache;

  public LlmInvoicePdfExtractor(
      ObjectProvider<ChatModel> chatModel,
      ObjectMapper mapper,
      LlmExtractionCacheRepository cache) {
    this.chatModel = chatModel;
    this.mapper = mapper;
    this.cache = cache;
  }

  @Override
  public PdfExtractedInvoice extract(String text) {
    ChatModel model = chatModel.getIfAvailable();
    if (model == null) {
      // No model configured: not a failure — the deterministic templates already ran and the LLM
      // fallback is simply unavailable. Return an empty invoice with no error so the caller treats
      // it as "no lines parsed" (PDF_UNPARSEABLE), not as an LLM failure, and environments without
      // an AI key don't fill the flag list.
      return new PdfExtractedInvoice(null, List.of());
    }
    String textSha256 = sha256Hex(text);
    Optional<PdfExtractedInvoice> cached = cache.findById(textSha256).flatMap(this::readCached);
    if (cached.isPresent()) {
      return cached.get();
    }
    PdfExtractedInvoice result = callLlm(model, text);
    // Cache only successful extractions: a transient model failure must be retried, not locked in.
    if (result.extractionError() == null) {
      cache.save(LlmExtractionCache.create(textSha256, toJson(result), Instant.now(CLOCK)));
    }
    return result;
  }

  private PdfExtractedInvoice callLlm(ChatModel model, String text) {
    try {
      ChatResponse response =
          model.call(
              new Prompt(
                  List.<Message>of(new SystemMessage(SCHEMA_PROMPT), new UserMessage(text)),
                  temperatureZero()));
      String content = response.getResult().getOutput().getText();
      return parse(content);
    } catch (RuntimeException e) {
      log.warn("LLM invoice extraction failed", e);
      return new PdfExtractedInvoice(null, List.of(), "LLM call failed: " + e.getMessage());
    }
  }

  private Optional<PdfExtractedInvoice> readCached(LlmExtractionCache cached) {
    try {
      PdfExtractedInvoice invoice =
          mapper.readValue(cached.extractedJson(), PdfExtractedInvoice.class);
      if (invoice != null && invoice.lines() != null) {
        return Optional.of(invoice);
      }
    } catch (Exception e) {
      log.warn("Cached LLM extraction was unreadable; re-extracting", e);
    }
    return Optional.empty();
  }

  private String toJson(PdfExtractedInvoice invoice) {
    try {
      return mapper.writeValueAsString(invoice);
    } catch (Exception e) {
      // Should not happen for this record; surface it rather than silently skip caching.
      throw new IllegalStateException("Could not serialize LLM extraction", e);
    }
  }

  private static String sha256Hex(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }

  private PdfExtractedInvoice parse(String content) {
    if (content == null || content.isBlank()) {
      return new PdfExtractedInvoice(null, List.of(), "LLM returned no output text");
    }
    String json = content;
    // Tolerate a model that wraps the JSON in a ```json … ``` fence or prepends a stray sentence.
    int open = content.indexOf('{');
    int close = content.lastIndexOf('}');
    if (open >= 0 && close > open) {
      json = content.substring(open, close + 1);
    }
    try {
      PdfExtractedInvoice invoice = mapper.readValue(json, PdfExtractedInvoice.class);
      // A schema-shaped object that omits `lines` (or emits `"lines": null`) deserializes to a
      // null list — treat it as unparseable rather than NPE-ing in the caller.
      if (invoice == null || invoice.lines() == null) {
        return new PdfExtractedInvoice(invoice == null ? null : invoice.invoiceNumber(), List.of());
      }
      return invoice;
    } catch (Exception e) {
      log.warn("Could not parse LLM invoice output as JSON contract", e);
      return new PdfExtractedInvoice(null, List.of(), "LLM output was not parseable JSON");
    }
  }

  private static ChatOptions temperatureZero() {
    return new DefaultChatOptionsBuilder().temperature(0.0).build();
  }
}
