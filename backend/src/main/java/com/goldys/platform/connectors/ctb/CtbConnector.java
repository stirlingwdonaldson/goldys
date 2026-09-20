package com.goldys.platform.connectors.ctb;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.nio.charset.StandardCharsets;

/**
 * Pulls CTB daily revenue through the authenticated AJAX endpoints and streams each page to the
 * sink immediately, so evidence accepted before a later page fails is already stored.
 */
public class CtbConnector implements SourceConnector {
  private static final int PAGE_SIZE = 200;
  private static final int MAX_PAGES = 250;

  private final CtbClient client;

  public CtbConnector(CtbClient client) {
    this.client = client;
  }

  @Override
  public String sourceSystem() {
    return "CTB";
  }

  @Override
  public String connectorName() {
    return "ctb-revenue";
  }

  @Override
  public void fetch(String watermark, IngestionSink sink) {
    int start = 0;
    int pages = 0;
    int total = -1;
    while (pages < MAX_PAGES) {
      CtbClient.CtbPage page = client.searchRevenues(start, PAGE_SIZE);
      sink.accept(
          new FetchedPayload(
              FetchMethod.API,
              "application/json",
              page.json().getBytes(StandardCharsets.UTF_8),
              StandardCharsets.UTF_8.name(),
              "ctb-revenue"));
      total = page.totalCount();
      pages++;
      if (page.json().isEmpty() || start + PAGE_SIZE >= total) {
        break;
      }
      start += PAGE_SIZE;
    }
  }
}
