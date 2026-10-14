package com.goldys.platform.classification;

import com.fasterxml.jackson.core.type.TypeReference;
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
 * LLM-backed {@link ClusterNamer}: prompts the Spring AI {@link ChatModel} (the same OpenAI model
 * wired for Ask Goldy's) to draft a category name, group and merge/split hint per flat-cut cluster,
 * then parses the JSON array back.
 *
 * <p>The model is injected as an {@link ObjectProvider} because it is absent when {@code
 * spring.ai.model.chat=none}. With no model the namer returns an empty list (no error); a model
 * that throws, or whose output cannot be parsed as the JSON contract, also yields an empty list —
 * naming is best-effort and never fails the run. The temperature is forced to 0 for determinism.
 */
@Component
public class OpenAiClusterNamer implements ClusterNamer {

  private static final Logger log = LoggerFactory.getLogger(OpenAiClusterNamer.class);

  private static final String SCHEMA_PROMPT =
      "You propose a product taxonomy for a pub menu. You are given clusters of products, each a "
          + "numbered list of product descriptions. Propose a short category name (1-3 words) and "
          + "an optional parent group for each cluster. You may merge clusters (give two clusters "
          + "the same category) or split a cluster (propose two categories for it); explain merges "
          + "and splits in the 'hint' field.\n"
          + "Return ONLY a JSON array matching this exact schema, no prose, no markdown:\n"
          + "[{\"name\": string, \"group\": string|null, \"hint\": string|null}]\n"
          + "Rules:\n"
          + "- name: a short human-readable category name (1-3 words).\n"
          + "- group: a parent group such as \"Beer\", \"Wine\", \"Food\", or null for a flat category.\n"
          + "- hint: a short note only when you merged or split clusters (e.g. \"merges clusters 0 "
          + "and 2\"), otherwise null.\n"
          + "- Every cluster must be covered; do not invent products.";

  private final ObjectProvider<ChatModel> chatModel;
  private final ObjectMapper mapper;

  public OpenAiClusterNamer(ObjectProvider<ChatModel> chatModel, ObjectMapper mapper) {
    this.chatModel = chatModel;
    this.mapper = mapper;
  }

  @Override
  public List<SuggestedCategory> suggest(List<ClusterSummary> clusters) {
    ChatModel model = chatModel.getIfAvailable();
    if (model == null || clusters.isEmpty()) {
      return List.of();
    }
    try {
      ChatResponse response =
          model.call(
              new Prompt(
                  List.<Message>of(
                      new SystemMessage(SCHEMA_PROMPT), new UserMessage(clusterText(clusters))),
                  temperatureZero()));
      return parse(response.getResult().getOutput().getText());
    } catch (RuntimeException e) {
      log.warn("LLM category naming failed", e);
      return List.of();
    }
  }

  private static String clusterText(List<ClusterSummary> clusters) {
    StringBuilder out = new StringBuilder();
    for (ClusterSummary cluster : clusters) {
      out.append("Cluster ")
          .append(cluster.index())
          .append(" (")
          .append(cluster.size())
          .append(" products):\n");
      for (String text : cluster.productTexts()) {
        out.append("- ").append(text).append('\n');
      }
    }
    return out.toString();
  }

  private List<SuggestedCategory> parse(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    // Tolerate a model that wraps the JSON in a ```json … ``` fence or prepends a stray sentence.
    String json = content;
    int open = content.indexOf('[');
    int close = content.lastIndexOf(']');
    if (open >= 0 && close > open) {
      json = content.substring(open, close + 1);
    }
    try {
      List<SuggestedCategory> result =
          mapper.readValue(json, new TypeReference<List<SuggestedCategory>>() {});
      return result == null ? List.of() : result;
    } catch (Exception e) {
      log.warn("Could not parse LLM category naming output as JSON", e);
      return List.of();
    }
  }

  private static ChatOptions temperatureZero() {
    return new DefaultChatOptionsBuilder().temperature(0.0).build();
  }
}
