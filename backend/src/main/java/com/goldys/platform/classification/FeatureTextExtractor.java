package com.goldys.platform.classification;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.CanonicalSaleItemQuery;
import com.goldys.platform.canonical.CanonicalSaleRecipeLinkQuery;
import com.goldys.platform.canonical.ClassificationEvidenceQuery;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.SaleRecipeLinkView;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Legacy name aliases are discovery candidates, never proof of source identity equivalence. */
@Service
public class FeatureTextExtractor {
  public record Candidate(String text, boolean ambiguous) {}

  private final CanonicalProductSalesQuery productSales;
  private final CanonicalSaleItemQuery saleItems;
  private final CanonicalSaleRecipeLinkQuery recipeLinks;
  private final ClassificationEvidenceQuery evidence;

  @Autowired
  public FeatureTextExtractor(
      CanonicalProductSalesQuery productSales,
      CanonicalSaleItemQuery saleItems,
      CanonicalSaleRecipeLinkQuery recipeLinks,
      ClassificationEvidenceQuery evidence) {
    this.productSales = productSales;
    this.saleItems = saleItems;
    this.recipeLinks = recipeLinks;
    this.evidence = evidence;
  }

  FeatureTextExtractor(
      CanonicalProductSalesQuery products,
      CanonicalSaleItemQuery items,
      CanonicalSaleRecipeLinkQuery links) {
    this(products, items, links, null);
  }

  public Map<String, String> featureTexts() {
    Map<String, String> texts = new LinkedHashMap<>();
    candidates().forEach((key, candidate) -> texts.put(key, candidate.text()));
    return texts;
  }

  public Map<String, Candidate> candidates() {
    Map<String, SortedSet<String>> texts = new TreeMap<>();
    Set<String> ambiguous = new HashSet<>();
    Map<String, Set<String>> identities = new HashMap<>();
    for (String key : productSales.distinctProductNameKeys()) add(texts, key, key);
    for (var item : saleItems.currentSaleItemIdentities()) {
      String key = ProductNameKey.normalize(item.itemName());
      add(texts, key, "POS name=" + item.itemName() + "; source category=" + item.categoryName());
    }
    Map<String, List<SaleRecipeLinkView>> links = new TreeMap<>();
    for (var link : recipeLinks.currentLinks()) {
      String key = ProductNameKey.normalize(link.saleItemStockDescription());
      links.computeIfAbsent(key, k -> new ArrayList<>()).add(link);
    }
    if (evidence != null) {
      for (var row : evidence.currentEvidence()) {
        String key = ProductNameKey.normalize(row.name());
        add(texts, key, "Source name=" + row.name() + "; " + row.text());
        if (row.ambiguous()) ambiguous.add(key);
        if (row.identity() != null && !row.identity().isBlank()) {
          Set<String> ids =
              identities.computeIfAbsent(key + "\u001f" + row.scope(), k -> new HashSet<>());
          ids.add(row.identity());
          if (ids.size() > 1) ambiguous.add(key);
        }
      }
    }
    Map<String, Candidate> result = new LinkedHashMap<>();
    texts.forEach(
        (key, parts) -> {
          List<SaleRecipeLinkView> observations = links.getOrDefault(key, List.of());
          Set<Integer> recipes = new HashSet<>();
          for (var link : observations) {
            recipes.add(link.recipeId());
            parts.add(
                "CTB link="
                    + link.saleItemRecipeId()
                    + "; recipe="
                    + link.recipeId()
                    + "; name="
                    + link.recipeName()
                    + "; folder="
                    + link.recipeFolderName()
                    + "; active="
                    + link.isRecipeActive()
                    + "; POS id="
                    + link.lightspeedProductId());
          }
          boolean conflict = ambiguous.contains(key) || recipes.size() > 1;
          parts.remove(key);
          String text = key;
          if (!parts.isEmpty()) text += "\n" + String.join("\n", parts.stream().limit(16).toList());
          if (parts.size() > 16)
            text += "\nEvidence truncated; " + parts.size() + " distinct observations.";
          if (text.length() > 8000) text = text.substring(0, 8000) + "\nEvidence truncated.";
          result.put(key, new Candidate(text, conflict));
        });
    return result;
  }

  private static void add(Map<String, SortedSet<String>> texts, String key, String text) {
    if (!key.isBlank()) texts.computeIfAbsent(key, k -> new TreeSet<>()).add(text);
  }
}
