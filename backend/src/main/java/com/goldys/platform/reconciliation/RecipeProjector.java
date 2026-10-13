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
import java.time.LocalDate;
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
 * normalized name) and its menu state, price source, and last-sold price/cost. Recipes are
 * single-source (CTB), so there is no conflict resolution.
 */
@Service
public class RecipeProjector {
  private static final Clock CLOCK = Clock.systemUTC();

  private static final String ON_MENU = "ON_MENU";
  private static final String OFF_MENU = "OFF_MENU";
  private static final String UNKNOWN = "UNKNOWN";
  private static final String CURRENT = "CURRENT";
  private static final String HISTORICAL = "HISTORICAL";
  private static final String NONE = "NONE";

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

    Map<String, PriceInfo> byName = priceIndex();

    // Group every current link (active and inactive) by recipe id so we can tell ON_MENU from
    // OFF_MENU and derive the last-sold price from inactive links.
    Map<Integer, List<SaleRecipeLinkView>> linksByRecipe = new HashMap<>();
    for (SaleRecipeLinkView link : links.currentLinks()) {
      linksByRecipe.computeIfAbsent(link.recipeId(), k -> new ArrayList<>()).add(link);
    }

    Instant now = CLOCK.instant();
    List<ResolvedRecipe> rows = new ArrayList<>();
    for (RecipeCostView c : recipeCosts.currentRecipeCosts()) {
      List<SaleRecipeLinkView> recipeLinks = linksByRecipe.getOrDefault(c.recipeId(), List.of());
      List<SaleRecipeLinkView> active = new ArrayList<>();
      List<SaleRecipeLinkView> inactive = new ArrayList<>();
      for (SaleRecipeLinkView link : recipeLinks) {
        if (Boolean.FALSE.equals(link.isRecipeActive())) {
          inactive.add(link);
        } else {
          active.add(link);
        }
      }

      String menuState =
          !active.isEmpty() ? ON_MENU : (!recipeLinks.isEmpty() ? OFF_MENU : UNKNOWN);
      String folder = active.isEmpty() ? null : active.get(0).recipeFolderName();

      // Current price: matched via the active links' sale-item descriptions, with the recipe name
      // as a fallback. The name fallback is suppressed when the recipe is off-menu, since a
      // same-named sale item is then spurious for the "current" price.
      List<String> currentKeys = new ArrayList<>();
      for (SaleRecipeLinkView link : active) {
        currentKeys.add(ProductNameKey.normalize(link.saleItemStockDescription()));
      }
      if (!OFF_MENU.equals(menuState)) {
        currentKeys.add(ProductNameKey.normalize(c.recipeName()));
      }
      List<BigDecimal> currentPrices = new ArrayList<>();
      List<BigDecimal> currentCosts = new ArrayList<>();
      for (String key : currentKeys) {
        PriceInfo info = byName.get(key);
        if (info != null) {
          currentPrices.add(info.price());
          currentCosts.add(info.cost());
        }
      }
      BigDecimal menuPrice = mode(currentPrices);
      BigDecimal actualCost = mode(currentCosts);

      // Last-sold: matched via the inactive links' descriptions plus the recipe name, taking the
      // most-recent trading date and the modal price/cost among the candidates on that date.
      List<String> historicalKeys = new ArrayList<>();
      for (SaleRecipeLinkView link : inactive) {
        historicalKeys.add(ProductNameKey.normalize(link.saleItemStockDescription()));
      }
      historicalKeys.add(ProductNameKey.normalize(c.recipeName()));

      LocalDate lastSoldDate = null;
      for (String key : historicalKeys) {
        PriceInfo info = byName.get(key);
        if (info != null
            && info.lastSoldDate() != null
            && (lastSoldDate == null || info.lastSoldDate().isAfter(lastSoldDate))) {
          lastSoldDate = info.lastSoldDate();
        }
      }
      BigDecimal lastSoldPrice = null;
      BigDecimal lastSoldCost = null;
      if (lastSoldDate != null) {
        List<BigDecimal> datedPrices = new ArrayList<>();
        List<BigDecimal> datedCosts = new ArrayList<>();
        for (String key : historicalKeys) {
          PriceInfo info = byName.get(key);
          if (info != null && lastSoldDate.equals(info.lastSoldDate())) {
            datedPrices.add(info.lastSoldPrice());
            datedCosts.add(info.lastSoldCost());
          }
        }
        lastSoldPrice = mode(datedPrices);
        lastSoldCost = mode(datedCosts);
      }

      String priceSource;
      BigDecimal menuPriceOut;
      BigDecimal actualCostOut;
      BigDecimal lastSoldPriceOut;
      BigDecimal lastSoldCostOut;
      LocalDate lastSoldDateOut;
      if (menuPrice != null) {
        priceSource = CURRENT;
        menuPriceOut = menuPrice;
        actualCostOut = actualCost;
        lastSoldPriceOut = null;
        lastSoldCostOut = null;
        lastSoldDateOut = null;
      } else if (lastSoldPrice != null) {
        priceSource = HISTORICAL;
        menuPriceOut = null;
        actualCostOut = null;
        lastSoldPriceOut = lastSoldPrice;
        lastSoldCostOut = lastSoldCost;
        lastSoldDateOut = lastSoldDate;
      } else {
        priceSource = NONE;
        menuPriceOut = null;
        actualCostOut = null;
        lastSoldPriceOut = null;
        lastSoldCostOut = null;
        lastSoldDateOut = null;
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
              menuPriceOut,
              actualCostOut,
              menuState,
              priceSource,
              lastSoldPriceOut,
              lastSoldCostOut,
              lastSoldDateOut,
              "SINGLE_SOURCE",
              "CTB",
              now));
    }
    resolved.saveAll(rows);
  }

  /**
   * Normalized sale-item name → aggregated price info: the modal sold price/cost across all items,
   * the most-recent (non-null) trading date, and the modal price/cost among items on that date.
   */
  private Map<String, PriceInfo> priceIndex() {
    Map<String, List<SaleItemPriceView>> itemsByName = new LinkedHashMap<>();
    for (SaleItemPriceView p : saleItems.currentSaleItemPrices()) {
      itemsByName
          .computeIfAbsent(ProductNameKey.normalize(p.itemName()), k -> new ArrayList<>())
          .add(p);
    }

    Map<String, PriceInfo> index = new LinkedHashMap<>();
    for (Map.Entry<String, List<SaleItemPriceView>> entry : itemsByName.entrySet()) {
      List<SaleItemPriceView> items = entry.getValue();
      List<BigDecimal> prices = new ArrayList<>();
      List<BigDecimal> costs = new ArrayList<>();
      LocalDate lastSoldDate = null;
      for (SaleItemPriceView p : items) {
        prices.add(p.soldPriceIncTax());
        costs.add(p.costIncTax());
        if (p.tradingDate() != null
            && (lastSoldDate == null || p.tradingDate().isAfter(lastSoldDate))) {
          lastSoldDate = p.tradingDate();
        }
      }
      BigDecimal lastSoldPrice = null;
      BigDecimal lastSoldCost = null;
      if (lastSoldDate != null) {
        List<BigDecimal> datedPrices = new ArrayList<>();
        List<BigDecimal> datedCosts = new ArrayList<>();
        for (SaleItemPriceView p : items) {
          if (lastSoldDate.equals(p.tradingDate())) {
            datedPrices.add(p.soldPriceIncTax());
            datedCosts.add(p.costIncTax());
          }
        }
        lastSoldPrice = mode(datedPrices);
        lastSoldCost = mode(datedCosts);
      }
      index.put(
          entry.getKey(),
          new PriceInfo(mode(prices), mode(costs), lastSoldDate, lastSoldPrice, lastSoldCost));
    }
    return index;
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

  private record PriceInfo(
      BigDecimal price,
      BigDecimal cost,
      LocalDate lastSoldDate,
      BigDecimal lastSoldPrice,
      BigDecimal lastSoldCost) {}
}
