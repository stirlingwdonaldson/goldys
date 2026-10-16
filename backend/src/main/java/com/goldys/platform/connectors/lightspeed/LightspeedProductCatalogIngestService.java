package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.canonical.ProductIngest;
import com.goldys.platform.canonical.ProductInput;
import com.goldys.platform.canonical.ProductMappingIngest;
import com.goldys.platform.canonical.SourceProductInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for the Lightspeed product catalog: persist the dropped CSV
 * byte-faithfully, parse it, record each product as a canonical fact, close products absent from
 * the latest snapshot, then re-resolve the sale-item→recipe links to products.
 */
@Service
public class LightspeedProductCatalogIngestService {
  private final IngestionService ingestion;
  private final LightspeedProductCatalogParser parser;
  private final ProductIngest productIngest;
  private final ProductMappingIngest productMapping;
  private final RecipeProductMatcher matcher;

  public LightspeedProductCatalogIngestService(
      IngestionService ingestion,
      LightspeedProductCatalogParser parser,
      ProductIngest productIngest,
      ProductMappingIngest productMapping,
      RecipeProductMatcher matcher) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.productIngest = productIngest;
    this.productMapping = productMapping;
    this.matcher = matcher;
  }

  public void ingest(byte[] csv) {
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "lightspeed-product-catalog",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            csv,
            StandardCharsets.UTF_8.name(),
            "lightspeed-product-catalog");

    List<LightspeedProduct> rows = parser.parse(csv);

    List<ProductInput> inputs =
        rows.stream()
            .map(
                p ->
                    new ProductInput(
                        "LIGHTSPEED",
                        p.productId(),
                        p.productName(),
                        p.category(),
                        p.sellPriceIncTax(),
                        p.isModifier(),
                        p.reportingGroup(),
                        rawId))
            .toList();
    productIngest.recordAll(inputs);

    for (LightspeedProduct p : rows) {
      productMapping.mapSourceProduct(
          new SourceProductInput(
              "LIGHTSPEED",
              "product:" + p.productId(),
              p.productId(),
              p.productName(),
              null,
              p.category(),
              rawId));
    }

    Set<Integer> parsedProductIds =
        rows.stream().map(LightspeedProduct::productId).collect(Collectors.toSet());
    productIngest.closeAbsent(parsedProductIds);

    matcher.match();
  }
}
