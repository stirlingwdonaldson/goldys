package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.ProductSalesInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionProcessing;
import com.goldys.platform.ingestion.IngestionService;
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

  private final IngestionService ingestion;
  private final IngestionProcessing processing;
  private final CanonicalProductSalesIngest canonical;
  private final LightspeedProductCsvParser parser;

  public LightspeedProductIngestService(
      IngestionService ingestion,
      CanonicalProductSalesIngest canonical,
      LightspeedProductCsvParser parser) {
    this.ingestion = ingestion;
    this.processing = new IngestionProcessing(ingestion);
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
    // Deliberately NOT the venue date (VenueClock): the report carries no date of its own, and if
    // Lightspeed delivers it early morning for the previous trading day, UTC arrival happens to be
    // the right day while the venue date would be one day late. Revisit this attribution rule once
    // the report's real delivery schedule is known — audit F11.
    LocalDate tradingDate = LocalDate.now();
    List<LightspeedProductSale> rows =
        processing.parse(rawId, () -> parser.parse(LightspeedEnvelope.csv(body), tradingDate));

    Map<String, ProductSalesInput> byProduct = new LinkedHashMap<>();
    for (LightspeedProductSale row : rows) {
      String key = ProductNameKey.normalize(row.productName());
      byProduct.merge(
          key,
          new ProductSalesInput(
              "LIGHTSPEED", tradingDate, key, row.quantitySold(), row.amount(), null, rawId),
          (a, b) ->
              new ProductSalesInput(
                  "LIGHTSPEED",
                  tradingDate,
                  key,
                  a.quantitySold().add(b.quantitySold()),
                  a.amount().add(b.amount()),
                  null,
                  rawId));
    }
    processing.canonicalize(rawId, byProduct.values(), canonical::record);
  }
}
