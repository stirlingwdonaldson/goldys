package com.goldys.platform.canonical;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the product-identity flow: record a source product, resolve (or create) the
 * business-product hub by name, and record the reviewable source→hub mapping. The source product's
 * code anchors the identity (confidence {@code SOURCE_CODE}); a code-less product is name-matched
 * (confidence {@code NAME}) and left for review.
 */
@Service
public class ProductMappingIngest {
  private final SourceProductService sourceProducts;
  private final ProductService products;
  private final ProductMappingService mappings;

  public ProductMappingIngest(
      SourceProductService sourceProducts,
      ProductService products,
      ProductMappingService mappings) {
    this.sourceProducts = sourceProducts;
    this.products = products;
    this.mappings = mappings;
  }

  /** Records a source product and maps it to the business-product hub, returning the hub id. */
  @Transactional
  public UUID mapSourceProduct(SourceProductInput input) {
    SourceProductIdentity source = sourceProducts.record(input);
    String hubName = ProductNameKey.normalize(input.name());
    if (hubName.isEmpty()) {
      hubName = "unknown:" + input.sourceSystem() + ":" + input.sourceRecordRef();
    }
    UUID hubId = products.resolve(hubName);
    // A name match that joins a hub already claimed by another source is a cross-source
    // assumption (sold vs purchased, two vendors) — record it as NAME, reviewable, not asserted.
    boolean crossSource = mappings.hasCrossSourceMapping(hubId, input.sourceSystem());
    String confidence = crossSource ? "NAME" : "SOURCE_CODE";
    mappings.map(
        new ProductMappingInput(
            input.sourceSystem(),
            source.sourceRecordRef(),
            hubId,
            confidence,
            "AUTO",
            input.rawRecordId()));
    return hubId;
  }

  /** Records or updates a mapping directly (manual review: accept/reject an automatic mapping). */
  public void map(ProductMappingInput input) {
    mappings.map(input);
  }
}
