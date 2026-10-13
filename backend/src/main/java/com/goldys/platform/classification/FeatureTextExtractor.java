package com.goldys.platform.classification;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.CanonicalSaleItemQuery;
import com.goldys.platform.canonical.CanonicalSaleRecipeLinkQuery;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.SaleItemIdentityView;
import com.goldys.platform.canonical.SaleRecipeLinkView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Builds the free-text description a classifier and the embedding service see for each product.
 *
 * <p>The candidate product set is the union of the canonical product-name keys (products with
 * sales) and the sale-item identities (menu items, whose {@code product_name_key} is derived by
 * {@link ProductNameKey#normalize}). The union is sorted and deduplicated so the candidate list —
 * and therefore the vector order fed to {@link EmbeddingClient} — is deterministic across runs.
 *
 * <p>For each key the feature text joins three pieces when present: the display name (the original,
 * un-normalized menu-item name), the POS category, and the CTB recipe folder/name. A product with
 * no matching sale item or recipe link yields its name alone, never an empty string.
 */
@Service
public class FeatureTextExtractor {

  private final CanonicalProductSalesQuery productSales;
  private final CanonicalSaleItemQuery saleItems;
  private final CanonicalSaleRecipeLinkQuery recipeLinks;

  public FeatureTextExtractor(
      CanonicalProductSalesQuery productSales,
      CanonicalSaleItemQuery saleItems,
      CanonicalSaleRecipeLinkQuery recipeLinks) {
    this.productSales = productSales;
    this.saleItems = saleItems;
    this.recipeLinks = recipeLinks;
  }

  /**
   * One feature text per candidate {@code product_name_key}, keyed and returned in sorted order.
   */
  public Map<String, String> featureTexts() {
    Map<String, SaleItemIdentityView> identities = new HashMap<>();
    for (SaleItemIdentityView identity : saleItems.currentSaleItemIdentities()) {
      identities.putIfAbsent(ProductNameKey.normalize(identity.itemName()), identity);
    }
    Map<String, SaleRecipeLinkView> links = new HashMap<>();
    for (SaleRecipeLinkView link : recipeLinks.currentLinks()) {
      links.putIfAbsent(ProductNameKey.normalize(link.saleItemStockDescription()), link);
    }

    SortedSet<String> keys = new TreeSet<>(productSales.distinctProductNameKeys());
    keys.addAll(identities.keySet());

    Map<String, String> result = new LinkedHashMap<>();
    for (String key : keys) {
      result.put(key, featureText(key, identities.get(key), links.get(key)));
    }
    return result;
  }

  private String featureText(String key, SaleItemIdentityView identity, SaleRecipeLinkView link) {
    List<String> parts = new ArrayList<>(4);
    String name = key;
    if (identity != null && notBlank(identity.itemName())) {
      name = identity.itemName();
    }
    parts.add(name);
    if (identity != null && notBlank(identity.categoryName())) {
      parts.add(identity.categoryName());
    }
    if (link != null) {
      if (notBlank(link.recipeFolderName())) {
        parts.add(link.recipeFolderName());
      }
      if (notBlank(link.recipeName())) {
        parts.add(link.recipeName());
      }
    }
    return String.join(" ", parts);
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
