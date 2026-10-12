package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-off backfill of the CTB line detail behind the header-only datasets: stocktake counted lines,
 * wastage line items, recipe ingredients, and CTB's derived per-recipe cost. Raw ledger only — no
 * canonicalization, no projection, no scheduling.
 *
 * <p>Re-fetches the three header lists (stocktakes, wastage, recipes) with the existing {@link
 * CtbClient} methods, then fans out one request per id, writing each response under its own {@code
 * fetcherIdentity} so the ledger distinguishes them. Failures are isolated per id — a failed detail
 * is logged and skipped while the remaining ids are still pulled — and rethrown aggregated at the
 * end so the run closes {@code PARTIAL} rather than silently dropping rows.
 *
 * <p>Not a Spring bean on purpose: {@code IngestionService} keys its connector map by {@code
 * sourceSystem}, and a second {@code CTB} bean would collide with the scheduled {@code
 * CtbConnector}. It is constructed explicitly by {@code CtbDetailBackfillRunner}.
 */
public class CtbDetailBackfillConnector implements SourceConnector {
  private static final Logger log = LoggerFactory.getLogger(CtbDetailBackfillConnector.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int HEADER_PAGE_SIZE = 200;
  private static final int DETAIL_PAGE_SIZE = 1000;
  private static final int MAX_PAGES = 250;
  private static final long INTER_REQUEST_DELAY_MS = 250;

  private final CtbClient client;
  private final String email;
  private final String password;

  public CtbDetailBackfillConnector(CtbClient client, String email, String password) {
    this.client = client;
    this.email = email;
    this.password = password;
  }

  @Override
  public String sourceSystem() {
    return "CTB";
  }

  @Override
  public String connectorName() {
    return "ctb-detail-backfill";
  }

  @Override
  public void fetch(String watermark, IngestionSink sink) {
    client.login(email, password);

    Map<String, ConnectorFetchException> failures = new LinkedHashMap<>();

    List<Integer> stocktakeIds = collectIds("stocktake-headers", failures, this::stocktakeIds);
    List<Integer> wastageIds = collectIds("wastage-headers", failures, this::wastageIds);
    List<Integer> recipeIds = collectIds("recipe-headers", failures, this::recipeIds);

    for (int id : stocktakeIds) {
      pull("ctb-stocktake-detail#" + id, failures, () -> pullStocktakeDetail(id, sink));
      politeDelay();
    }
    for (int id : wastageIds) {
      pull("ctb-wastage-detail#" + id, failures, () -> pullWastageDetail(id, sink));
      politeDelay();
    }
    for (int id : recipeIds) {
      pull("ctb-recipe-ingredients#" + id, failures, () -> pullRecipeIngredients(id, sink));
      politeDelay();
    }
    if (!recipeIds.isEmpty()) {
      pull("ctb-recipe-cost", failures, () -> pullRecipeCost(recipeIds, sink));
    }

    if (!failures.isEmpty()) {
      throw aggregate(failures);
    }
  }

  /**
   * Attempt one detail request, recording a classified failure instead of aborting the backfill.
   */
  private static void pull(
      String key, Map<String, ConnectorFetchException> failures, Runnable attempt) {
    try {
      attempt.run();
    } catch (ConnectorFetchException e) {
      log.warn("CTB detail backfill {} failed: {}: {}", key, e.failureType(), e.getMessage());
      failures.put(key, e);
    }
  }

  /** Fetch a header list, recording a classified failure and returning no ids on failure. */
  private static List<Integer> collectIds(
      String key, Map<String, ConnectorFetchException> failures, Supplier<List<Integer>> ids) {
    try {
      return ids.get();
    } catch (ConnectorFetchException e) {
      log.warn("CTB detail backfill {} failed: {}: {}", key, e.failureType(), e.getMessage());
      failures.put(key, e);
      return List.of();
    }
  }

  /**
   * A single failed request is rethrown verbatim so its failure type and stack stay intact; several
   * are summarised (with each request's own detail) so the run records what is missing.
   */
  private static ConnectorFetchException aggregate(Map<String, ConnectorFetchException> failures) {
    if (failures.size() == 1) {
      return failures.values().iterator().next();
    }
    String firstType = failures.values().iterator().next().failureType();
    StringBuilder detail = new StringBuilder("CTB detail backfill failed:");
    for (Map.Entry<String, ConnectorFetchException> entry : failures.entrySet()) {
      detail
          .append("\n- ")
          .append(entry.getKey())
          .append(": ")
          .append(entry.getValue().failureType())
          .append(": ")
          .append(entry.getValue().getMessage());
    }
    return new ConnectorFetchException(firstType, detail.toString());
  }

  private List<Integer> stocktakeIds() {
    List<Integer> ids = new ArrayList<>();
    int start = 0;
    int pages = 0;
    int total = -1;
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = client.searchStocktakes(start, HEADER_PAGE_SIZE);
      ids.addAll(readIds(page.json(), "stocktakeId"));
      total = page.totalCount();
      pages++;
      if (start + HEADER_PAGE_SIZE >= total) {
        break;
      }
      start += HEADER_PAGE_SIZE;
    }
    return ids;
  }

  private List<Integer> wastageIds() {
    List<Integer> ids = new ArrayList<>();
    int start = 0;
    int pages = 0;
    int total = -1;
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = client.searchWastageRecords(start, HEADER_PAGE_SIZE);
      ids.addAll(readIds(page.json(), "wastageRecordId"));
      total = page.totalCount();
      pages++;
      if (start + HEADER_PAGE_SIZE >= total) {
        break;
      }
      start += HEADER_PAGE_SIZE;
    }
    return ids;
  }

  private List<Integer> recipeIds() {
    return readIds(client.getAllRecipes().json(), "recipeId");
  }

  private void pullStocktakeDetail(int stocktakeId, IngestionSink sink) {
    int start = 0;
    int pages = 0;
    int total = -1;
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = client.getStocktakeDetail(stocktakeId, start, DETAIL_PAGE_SIZE);
      writeRaw(page.json(), "ctb-stocktake-detail", "stocktake:" + stocktakeId, sink);
      total = page.totalCount();
      pages++;
      if (start + DETAIL_PAGE_SIZE >= total) {
        break;
      }
      start += DETAIL_PAGE_SIZE;
    }
  }

  private void pullWastageDetail(int wastageRecordId, IngestionSink sink) {
    writeRaw(
        client.getWastageRecordDetail(wastageRecordId).json(),
        "ctb-wastage-detail",
        "wastage:" + wastageRecordId,
        sink);
  }

  private void pullRecipeIngredients(int recipeId, IngestionSink sink) {
    writeRaw(
        client.getIngredientsIncludingSubRecipeIngredients(recipeId).json(),
        "ctb-recipe-ingredients",
        "recipe:" + recipeId,
        sink);
  }

  private void pullRecipeCost(List<Integer> recipeIds, IngestionSink sink) {
    writeRaw(
        client.getSelectedRecipeFullRecords(jsonArray(recipeIds)).json(),
        "ctb-recipe-cost",
        null,
        sink);
  }

  private static List<Integer> readIds(String json, String field) {
    try {
      JsonNode data = MAPPER.readTree(json).path("data");
      List<Integer> ids = new ArrayList<>();
      if (data.isArray()) {
        for (JsonNode item : data) {
          ids.add(item.path(field).asInt());
        }
      }
      return ids;
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response", e);
    }
  }

  private static String jsonArray(List<Integer> ids) {
    try {
      return MAPPER.writeValueAsString(ids);
    } catch (JsonProcessingException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Failed to encode recipe ids", e);
    }
  }

  private static void writeRaw(
      String json, String fetcherIdentity, String sourceRecordRef, IngestionSink sink) {
    sink.accept(
        new FetchedPayload(
            FetchMethod.API,
            "application/json",
            json.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8.name(),
            fetcherIdentity,
            sourceRecordRef));
  }

  /** Small inter-request delay so the ~810-request one-off pass stays polite to CTB. */
  private static void politeDelay() {
    try {
      Thread.sleep(INTER_REQUEST_DELAY_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "Interrupted during backfill delay", e);
    }
  }
}
