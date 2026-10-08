package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
 * invoice — a caller that reaches the LLM and gets nothing back treats that as "unparseable", never
 * as a hallucinated success. The JSON contract is fixed and the temperature is forced to 0 so the
 * fallback is as deterministic as a probabilistic model can be.
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
          + "\"unitQuantity\": number|null, \"packSize\": number|null, \"wetAmount\": number|null}]}\n"
          + "Rules:\n"
          + "- invoiceNumber: the invoice number printed on the invoice, or null if absent.\n"
          + "- stockCode: the supplier's stock/code column value, or null if absent.\n"
          + "- description: the line item description.\n"
          + "- quantity: the shipped/invoiced quantity (numeric only).\n"
          + "- uom: the unit of measure (e.g. KG, EACH, CTN, LT) from the UNIT/UOM column, or null.\n"
          + "- unitQuantity: the per-pack unit count (e.g. 48 in '48 EACH'), or null.\n"
          + "- packSize: the pack/carton size (e.g. 4 in '4 CTN'), or null.\n"
          + "- wetAmount: the Wine Equalisation Tax amount for the line (liquor only), or null.\n"
          + "Never invent a value. When a field is not present in the text, emit null for it.";

  private final ObjectProvider<ChatModel> chatModel;
  private final ObjectMapper mapper;

  public LlmInvoicePdfExtractor(ObjectProvider<ChatModel> chatModel, ObjectMapper mapper) {
    this.chatModel = chatModel;
    this.mapper = mapper;
  }

  @Override
  public PdfExtractedInvoice extract(String text) {
    ChatModel model = chatModel.getIfAvailable();
    if (model == null) {
      return new PdfExtractedInvoice(null, List.of());
    }
    try {
      ChatResponse response =
          model.call(
              new Prompt(
                  List.<Message>of(new SystemMessage(SCHEMA_PROMPT), new UserMessage(text)),
                  temperatureZero()));
      String content = response.getResult().getOutput().getText();
      return parse(content);
    } catch (RuntimeException e) {
      log.warn("LLM invoice extraction failed; returning empty result", e);
      return new PdfExtractedInvoice(null, List.of());
    }
  }

  private PdfExtractedInvoice parse(String content) {
    if (content == null || content.isBlank()) {
      return new PdfExtractedInvoice(null, List.of());
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
      return new PdfExtractedInvoice(null, List.of());
    }
  }

  private static ChatOptions temperatureZero() {
    return new DefaultChatOptionsBuilder().temperature(0.0).build();
  }
}
