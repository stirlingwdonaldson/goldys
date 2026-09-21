package com.goldys.platform.connectors.lightspeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.ProductSalesInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Full Lightspeed per-product webhook pipeline: persist the Looker envelope byte-faithfully, then
 * parse the embedded CSV and canonicalize per-product rows.
 */
@Service
public class LightspeedProductIngestService {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final IngestionService ingestion;
  private final CanonicalProductSalesIngest canonical;
  private final LightspeedProductCsvParser parser;

  public LightspeedProductIngestService(
      IngestionService ingestion,
      CanonicalProductSalesIngest canonical,
      LightspeedProductCsvParser parser) {
    this.ingestion = ingestion;
    this.canonical = canonical;
    this.parser = parser;
  }

  public void ingest(byte[] body) {
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "lightspeed-products",
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            StandardCharsets.UTF_8.name(),
            "lightspeed-products");

    // The "Sales By" report has no per-row date; attribute to the day the report arrived.
    LocalDate tradingDate = LocalDate.now();
    List<LightspeedProductSale> rows =
        parser.parse(extractCsv(body).getBytes(StandardCharsets.UTF_8), tradingDate);

    Map<String, ProductSalesInput> byProduct = new LinkedHashMap<>();
    for (LightspeedProductSale row : rows) {
      String key = ProductNameKey.normalize(row.productName());
      byProduct.merge(
          key,
          new ProductSalesInput(
              "LIGHTSPEED", tradingDate, key, row.quantitySold(), row.amount(), rawId),
          (a, b) ->
              new ProductSalesInput(
                  "LIGHTSPEED",
                  tradingDate,
                  key,
                  a.quantitySold().add(b.quantitySold()),
                  a.amount().add(b.amount()),
                  rawId));
    }
    for (ProductSalesInput input : byProduct.values()) {
      canonical.record(input);
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
}
