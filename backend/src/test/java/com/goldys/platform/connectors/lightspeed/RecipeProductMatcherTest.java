package com.goldys.platform.connectors.lightspeed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalProductQuery;
import com.goldys.platform.canonical.CanonicalSaleRecipeLinkQuery;
import com.goldys.platform.canonical.ProductAliasQuery;
import com.goldys.platform.canonical.ProductAliasView;
import com.goldys.platform.canonical.ProductView;
import com.goldys.platform.canonical.SaleRecipeLinkIngest;
import com.goldys.platform.canonical.SaleRecipeLinkView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeProductMatcherTest {

  @Mock CanonicalProductQuery productQuery;
  @Mock ProductAliasQuery aliasQuery;
  @Mock CanonicalSaleRecipeLinkQuery linkQuery;
  @Mock SaleRecipeLinkIngest linkIngest;

  private RecipeProductMatcher matcher() {
    return new RecipeProductMatcher(productQuery, aliasQuery, linkQuery, linkIngest);
  }

  private static ProductView product(int id, String name) {
    return new ProductView(id, name, null, null, false, null);
  }

  private static SaleRecipeLinkView link(
      int saleItemRecipeId, String description, Integer productId) {
    return new SaleRecipeLinkView(
        1, "Recipe", "Folder", description, true, saleItemRecipeId, productId);
  }

  @Test
  void matchesByNameAndSetsProductId() {
    when(productQuery.currentProducts()).thenReturn(List.of(product(1, "Pint - Keeper Pilsner")));
    when(aliasQuery.all()).thenReturn(List.of());
    when(linkQuery.currentLinks()).thenReturn(List.of(link(10, "Pint - Keeper Pilsner", null)));

    matcher().match();

    verify(linkIngest).setProductId(10, 1);
  }

  @Test
  void aliasResolves() {
    when(productQuery.currentProducts()).thenReturn(List.of(product(2, "Baba Ganoush")));
    when(aliasQuery.all())
        .thenReturn(List.of(new ProductAliasView("baba ganoush", "eggplant dip")));
    when(linkQuery.currentLinks()).thenReturn(List.of(link(11, "Eggplant Dip", null)));

    matcher().match();

    verify(linkIngest).setProductId(11, 2);
  }

  @Test
  void idFastPathPreservesExisting() {
    when(productQuery.currentProducts()).thenReturn(List.of(product(1, "Pint - Keeper Pilsner")));
    when(aliasQuery.all()).thenReturn(List.of());
    when(linkQuery.currentLinks()).thenReturn(List.of(link(12, "Pint - Keeper Pilsner", 1)));

    matcher().match();

    verify(linkIngest, never()).setProductId(anyInt(), any());
  }

  @Test
  void unmatchedSetsNull() {
    when(productQuery.currentProducts()).thenReturn(List.of());
    when(aliasQuery.all()).thenReturn(List.of());
    // The link still points at a product (99) that has since disappeared from the snapshot.
    when(linkQuery.currentLinks()).thenReturn(List.of(link(13, "Satay Schnitty", 99)));

    matcher().match();

    verify(linkIngest).setProductId(13, null);
  }
}
