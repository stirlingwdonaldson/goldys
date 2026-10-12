package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalRecipeCostQuery;
import com.goldys.platform.canonical.CanonicalSaleItemQuery;
import com.goldys.platform.canonical.CanonicalSaleRecipeLinkQuery;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.RecipeCostView;
import com.goldys.platform.canonical.SaleItemPriceView;
import com.goldys.platform.canonical.SaleRecipeLinkView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the recipe read model ({@code resolved_recipe}) from canonical recipe costs and
 * sale-recipe links, enriched with each recipe's Lightspeed menu price and actual cost (matched by
 * normalized name). Recipes are single-source (CTB), so there is no conflict resolution.
 */
@Service
public class RecipeProjector {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalRecipeCostQuery recipeCosts;
  private final CanonicalSaleRecipeLinkQuery links;
  private final CanonicalSaleItemQuery saleItems;
  private final ResolvedRecipeRepository resolved;

  public RecipeProjector(
      CanonicalRecipeCostQuery recipeCosts,
      CanonicalSaleRecipeLinkQuery links,
      CanonicalSaleItemQuery saleItems,
      ResolvedRecipeRepository resolved) {
    this.recipeCosts = recipeCosts;
    this.links = links;
    this.saleItems = saleItems;
    this.resolved = resolved;
  }

  /** Rebuild the whole read model from canonical facts. */
  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();

    Map<String, Mode> byName = priceModes();
    Map<Integer, List<SaleRecipeLinkView>> linksByRecipe = new HashMap<>();
    for (SaleRecipeLinkView link : links.currentLinks()) {
      // Inactive links (is_recipe_active=false) mean the dish is no longer on the menu, so it
      // contributes no folder, price or cost. A null flag is treated as active.
      if (Boolean.FALSE.equals(link.isRecipeActive())) {
        continue;
      }
      linksByRecipe.computeIfAbsent(link.recipeId(), k -> new ArrayList<>()).add(link);
    }

    Instant now = CLOCK.instant();
    List<ResolvedRecipe> rows = new ArrayList<>();
    for (RecipeCostView c : recipeCosts.currentRecipeCosts()) {
      List<SaleRecipeLinkView> recipeLinks = linksByRecipe.getOrDefault(c.recipeId(), List.of());
      String folder = recipeLinks.isEmpty() ? null : recipeLinks.get(0).recipeFolderName();
      List<BigDecimal> prices = new ArrayList<>();
      List<BigDecimal> costs = new ArrayList<>();
      for (SaleRecipeLinkView link : recipeLinks) {
        Mode m = byName.get(ProductNameKey.normalize(link.saleItemStockDescription()));
        if (m != null) {
          prices.add(m.price);
          costs.add(m.cost);
        }
      }
      rows.add(
          new ResolvedRecipe(
              c.recipeId(),
              c.recipeName(),
              folder,
              c.costPerPortion(),
              c.costPerPortionInclLabour(),
              c.totalCost(),
              c.portionSize(),
              c.portionSizeValue(),
              mode(prices),
              mode(costs),
              "SINGLE_SOURCE",
              "CTB",
              now));
    }
    resolved.saveAll(rows);
  }

  /** Normalized sale-item name → mode sold price / mode cost. */
  private Map<String, Mode> priceModes() {
    Map<String, List<BigDecimal>> pricesByName = new LinkedHashMap<>();
    Map<String, List<BigDecimal>> costsByName = new LinkedHashMap<>();
    for (SaleItemPriceView p : saleItems.currentSaleItemPrices()) {
      String name = ProductNameKey.normalize(p.itemName());
      pricesByName.computeIfAbsent(name, k -> new ArrayList<>()).add(p.soldPriceIncTax());
      costsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(p.costIncTax());
    }
    Map<String, Mode> byName = new LinkedHashMap<>();
    for (String name : pricesByName.keySet()) {
      byName.put(name, new Mode(mode(pricesByName.get(name)), mode(costsByName.get(name))));
    }
    return byName;
  }

  /** The most frequent value, or null when empty/all-null; ties keep the first seen. */
  private static BigDecimal mode(List<BigDecimal> values) {
    Map<BigDecimal, Long> counts = new LinkedHashMap<>();
    for (BigDecimal v : values) {
      if (v != null) {
        counts.merge(v, 1L, Long::sum);
      }
    }
    BigDecimal best = null;
    long bestCount = -1;
    for (Map.Entry<BigDecimal, Long> e : counts.entrySet()) {
      if (e.getValue() > bestCount) {
        bestCount = e.getValue();
        best = e.getKey();
      }
    }
    return best;
  }

  private record Mode(BigDecimal price, BigDecimal cost) {}
}
