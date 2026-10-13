package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.canonical.CanonicalProductQuery;
import com.goldys.platform.canonical.CanonicalSaleRecipeLinkQuery;
import com.goldys.platform.canonical.ProductAliasQuery;
import com.goldys.platform.canonical.ProductAliasView;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.ProductView;
import com.goldys.platform.canonical.SaleRecipeLinkIngest;
import com.goldys.platform.canonical.SaleRecipeLinkView;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Resolves each current sale-item→recipe link to the Lightspeed product it represents, and records
 * the resolved product id on the link.
 *
 * <p>The resolution order per link is: (1) keep the existing product id if that product is still
 * present in the current product snapshot (the id fast-path); (2) match the link's stock
 * description directly against a current product's normalized name; (3) match via the manual alias
 * table. If none resolve, the link's product id is cleared ({@code null}) so a stale pointer never
 * survives a re-import.
 */
@Service
public class RecipeProductMatcher {
  private final CanonicalProductQuery productQuery;
  private final ProductAliasQuery aliasQuery;
  private final CanonicalSaleRecipeLinkQuery linkQuery;
  private final SaleRecipeLinkIngest linkIngest;

  public RecipeProductMatcher(
      CanonicalProductQuery productQuery,
      ProductAliasQuery aliasQuery,
      CanonicalSaleRecipeLinkQuery linkQuery,
      SaleRecipeLinkIngest linkIngest) {
    this.productQuery = productQuery;
    this.aliasQuery = aliasQuery;
    this.linkQuery = linkQuery;
    this.linkIngest = linkIngest;
  }

  public void match() {
    var products = productQuery.currentProducts();
    Set<Integer> currentIds = new HashSet<>();
    Map<String, Integer> productByName = new HashMap<>();
    for (ProductView p : products) {
      currentIds.add(p.productId());
      productByName.put(ProductNameKey.normalize(p.productName()), p.productId());
    }

    // Both sides of the alias pair are already normalized by the alias seeder.
    Map<String, String> aliasToCanonical = new HashMap<>();
    for (ProductAliasView a : aliasQuery.all()) {
      aliasToCanonical.putIfAbsent(a.alias(), a.canonicalName());
    }

    for (SaleRecipeLinkView link : linkQuery.currentLinks()) {
      Integer existing = link.lightspeedProductId();
      Integer target = (existing != null && currentIds.contains(existing)) ? existing : null;
      if (target == null) {
        String key = ProductNameKey.normalize(link.saleItemStockDescription());
        target = productByName.get(key);
        if (target == null) {
          String canonical = aliasToCanonical.get(key);
          if (canonical != null) {
            target = productByName.get(canonical);
          }
        }
      }
      if (!Objects.equals(target, existing)) {
        linkIngest.setProductId(link.saleItemRecipeId(), target);
      }
    }
  }
}
