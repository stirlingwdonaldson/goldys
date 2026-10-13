package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.canonical.CanonicalProductQuery;
import com.goldys.platform.canonical.CanonicalSaleRecipeLinkQuery;
import com.goldys.platform.canonical.ProductAliasQuery;
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
 * table (deterministically, via {@link ProductAliasQuery#lookupCanonical}). If none resolve, the
 * link's existing id is kept: a non-null id is a retirement signal (so the projector reads {@code
 * OFF_MENU} rather than {@code UNKNOWN}), and a null id simply stays {@code null} (never-matched →
 * {@code UNKNOWN}).
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

    for (SaleRecipeLinkView link : linkQuery.currentLinks()) {
      Integer existing = link.lightspeedProductId();
      // A non-null id is a retirement signal: it stays set unless a name/alias re-match resolves
      // it to a different (still-present) product. Only a null id means "never matched".
      Integer target = existing;
      if (existing == null || !currentIds.contains(existing)) {
        String key = ProductNameKey.normalize(link.saleItemStockDescription());
        if (productByName.containsKey(key)) {
          target = productByName.get(key);
        } else {
          String canonical = aliasQuery.lookupCanonical(key).orElse(null);
          if (canonical != null && productByName.containsKey(canonical)) {
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
