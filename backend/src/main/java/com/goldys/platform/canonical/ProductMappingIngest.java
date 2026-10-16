package com.goldys.platform.canonical;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the product-identity flow: record a source product, resolve (or create) the
 * business-product hub by name, and record the reviewable source→hub mapping. The source product's
 * code anchors its source identity. New cross-source hub associations are name-matched (confidence
 * {@code NAME}) and left for review; existing reviewed mappings are retained.
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
    var existing = mappings.currentProduct(input.sourceSystem(), source.sourceRecordRef());
    if (existing.isPresent()) return existing.get();
    String hubName = ProductNameKey.normalize(input.name());
    if (hubName.isEmpty()) {
      hubName = "unknown:" + input.sourceSystem() + ":" + input.sourceRecordRef();
    }
    UUID hubId = products.resolve(hubName);
    // The source code establishes source identity, not a verified cross-source hub match.
    String confidence = "NAME";
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
