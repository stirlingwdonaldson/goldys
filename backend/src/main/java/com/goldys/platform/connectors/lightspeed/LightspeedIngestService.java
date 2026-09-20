package com.goldys.platform.connectors.lightspeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Full Lightspeed webhook pipeline: persist the Looker envelope byte-faithfully, then parse the
 * embedded CSV and aggregate per-sale rows into canonical daily totals.
 */
@Service
public class LightspeedIngestService {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final IngestionService ingestion;
  private final CanonicalDailySalesIngest canonical;
  private final LightspeedInsightsCsvParser parser;

  public LightspeedIngestService(
      IngestionService ingestion,
      CanonicalDailySalesIngest canonical,
      LightspeedInsightsCsvParser parser) {
    this.ingestion = ingestion;
    this.canonical = canonical;
    this.parser = parser;
  }

  public void ingest(byte[] body) {
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "lightspeed-insights",
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            StandardCharsets.UTF_8.name(),
            "lightspeed-insights");

    List<LightspeedInsightsSale> sales =
        parser.parse(extractCsv(body).getBytes(StandardCharsets.UTF_8));

    Map<LocalDate, Totals> byDate = new LinkedHashMap<>();
    for (LightspeedInsightsSale sale : sales) {
      byDate.computeIfAbsent(sale.saleDate(), d -> new Totals()).add(sale);
    }

    for (Map.Entry<LocalDate, Totals> entry : byDate.entrySet()) {
      Totals t = entry.getValue();
      canonical.record(
          new DailySalesInput(
              "LIGHTSPEED", entry.getKey(), t.total, t.gst, t.total.subtract(t.gst), rawId));
    }
  }

  private static String extractCsv(byte[] body) {
    try {
      JsonNode root = MAPPER.readTree(body);
      String data = root.path("attachment").path("data").asText(null);
      if (data == null) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "No attachment.data in Lightspeed webhook payload");
      }
      return data;
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON webhook payload", e);
    }
  }

  private static final class Totals {
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal gst = BigDecimal.ZERO;

    void add(LightspeedInsightsSale sale) {
      total = total.add(nz(sale.totalIncTax()));
      gst = gst.add(nz(sale.totalTax()));
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
