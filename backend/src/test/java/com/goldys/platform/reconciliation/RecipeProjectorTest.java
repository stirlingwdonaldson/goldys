package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalProductQuery;
import com.goldys.platform.canonical.CanonicalRecipeCostQuery;
import com.goldys.platform.canonical.CanonicalSaleItemQuery;
import com.goldys.platform.canonical.CanonicalSaleRecipeLinkQuery;
import com.goldys.platform.canonical.ProductView;
import com.goldys.platform.canonical.RecipeCostView;
import com.goldys.platform.canonical.SaleItemPriceView;
import com.goldys.platform.canonical.SaleRecipeLinkView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecipeProjectorTest {

  private static final LocalDate d = LocalDate.of(2026, 10, 1);

  private final CanonicalRecipeCostQuery recipeCosts = mock(CanonicalRecipeCostQuery.class);
  private final CanonicalSaleRecipeLinkQuery links = mock(CanonicalSaleRecipeLinkQuery.class);
  private final CanonicalSaleItemQuery saleItems = mock(CanonicalSaleItemQuery.class);
  private final CanonicalProductQuery products = mock(CanonicalProductQuery.class);
  private final ResolvedRecipeRepository resolved = mock(ResolvedRecipeRepository.class);
  private final RecipeProjector projector =
      new RecipeProjector(recipeCosts, links, saleItems, products, resolved);

  @Test
  void projectsCostAndFolder() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks()).thenReturn(List.of(link(1, "Parma", "Mains", "Parma")));
    when(saleItems.currentSaleItemPrices()).thenReturn(List.of());

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.recipeId()).isEqualTo(1);
    assertThat(r.recipeName()).isEqualTo("Parma");
    assertThat(r.recipeFolderName()).isEqualTo("Mains");
    assertThat(r.costPerPortion()).isEqualByComparingTo("6.00");
    assertThat(r.menuPrice()).isNull();
    assertThat(r.actualCost()).isNull();
    assertThat(r.resolutionType()).isEqualTo("SINGLE_SOURCE");
    assertThat(r.authoritativeSource()).isEqualTo("CTB");
  }

  @Test
  void joinsPriceByNormalizedNameWithNewAlias() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Kids Fish", "6.00")));
    when(links.currentLinks()).thenReturn(List.of(link(1, "Kids Fish", "Food", "Kids Fish")));
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "New Kids Fish",
                    new BigDecimal("18.50"),
                    new BigDecimal("6.00"),
                    LocalDate.of(2026, 10, 1))));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.menuPrice()).isEqualByComparingTo("18.50");
    assertThat(r.actualCost()).isEqualByComparingTo("6.00");
  }

  @Test
  void modeNotMean() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks()).thenReturn(List.of(link(1, "Parma", "Mains", "Parma")));
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Parma",
                    new BigDecimal("18.50"),
                    new BigDecimal("6.00"),
                    LocalDate.of(2026, 10, 1)),
                new SaleItemPriceView(
                    "Parma",
                    new BigDecimal("18.50"),
                    new BigDecimal("6.00"),
                    LocalDate.of(2026, 10, 1)),
                new SaleItemPriceView(
                    "Parma",
                    new BigDecimal("2.00"),
                    new BigDecimal("1.00"),
                    LocalDate.of(2026, 10, 1))));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.menuPrice()).isEqualByComparingTo("18.50");
    assertThat(r.actualCost()).isEqualByComparingTo("6.00");
  }

  @Test
  void noMatchYieldsNullPriceAndCost() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks()).thenReturn(List.of(link(1, "Parma", "Mains", "Parma")));
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Something Else",
                    new BigDecimal("5.00"),
                    new BigDecimal("2.00"),
                    LocalDate.of(2026, 10, 1))));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.menuPrice()).isNull();
    assertThat(r.actualCost()).isNull();
  }

  @Test
  void recomputeAllRebuildsCleanly() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks()).thenReturn(List.of(link(1, "Parma", "Mains", "Parma")));
    when(saleItems.currentSaleItemPrices()).thenReturn(List.of());

    projector.recomputeAll();
    projector.recomputeAll();

    verify(resolved, times(2)).deleteAllInBatch();
  }

  @Test
  void inactiveLinkYieldsNullPriceAndFolder() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks()).thenReturn(List.of(link(1, "Parma", "Mains", "Parma", false)));
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Parma",
                    new BigDecimal("18.50"),
                    new BigDecimal("6.00"),
                    LocalDate.of(2026, 10, 1))));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.recipeFolderName()).isNull();
    assertThat(r.menuPrice()).isNull();
    assertThat(r.actualCost()).isNull();
  }

  @Test
  void recipeNameMatchWithoutLinkSetsCurrentPriceAndUnknownState() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks()).thenReturn(List.of());
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Parma", new BigDecimal("18.50"), new BigDecimal("6.00"), d)));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.menuPrice()).isEqualByComparingTo("18.50");
    assertThat(r.actualCost()).isEqualByComparingTo("6.00");
    assertThat(r.priceSource()).isEqualTo("CURRENT");
    assertThat(r.menuState()).isEqualTo("UNKNOWN");
    assertThat(r.lastSoldPrice()).isNull();
  }

  @Test
  void recipeNameRecoversWhenLinkDescriptionDoesNotMatch() {
    when(recipeCosts.currentRecipeCosts())
        .thenReturn(List.of(cost(1, "Dolin Dry Vermouth", "6.00")));
    when(links.currentLinks())
        .thenReturn(List.of(link(1, "Dolin Dry Vermouth", "Mains", "Dolin Dry Vermouth - 45ml")));
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Dolin Dry Vermouth", new BigDecimal("9.00"), new BigDecimal("3.00"), d)));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.priceSource()).isEqualTo("CURRENT");
    assertThat(r.menuState()).isEqualTo("UNKNOWN");
    assertThat(r.menuPrice()).isEqualByComparingTo("9.00");
    assertThat(r.lastSoldPrice()).isNull();
  }

  @Test
  void noLinkNoNameMatchYieldsNoneAndUnknown() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Pickle Brine", "6.00")));
    when(links.currentLinks()).thenReturn(List.of());
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Something Else", new BigDecimal("5.00"), new BigDecimal("2.00"), d)));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.priceSource()).isEqualTo("NONE");
    assertThat(r.menuState()).isEqualTo("UNKNOWN");
    assertThat(r.menuPrice()).isNull();
    assertThat(r.actualCost()).isNull();
    assertThat(r.lastSoldPrice()).isNull();
  }

  @Test
  void inactiveLinkYieldsHistoricalPriceAndUnknownState() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks()).thenReturn(List.of(link(1, "Parma", "Mains", "Parma", false)));
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Parma", new BigDecimal("18.50"), new BigDecimal("6.00"), d)));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.menuPrice()).isNull();
    assertThat(r.priceSource()).isEqualTo("HISTORICAL");
    assertThat(r.menuState()).isEqualTo("UNKNOWN");
    assertThat(r.lastSoldPrice()).isEqualByComparingTo("18.50");
    assertThat(r.lastSoldCost()).isEqualByComparingTo("6.00");
    assertThat(r.lastSoldDate()).isEqualTo(d);
  }

  @Test
  void aliasGapStaysNoneAndUnknown() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Baba Ganoush", "6.00")));
    when(links.currentLinks())
        .thenReturn(List.of(link(1, "Baba Ganoush", "Mains", "Eggplant Dip")));
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Something Else", new BigDecimal("5.00"), new BigDecimal("2.00"), d)));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.priceSource()).isEqualTo("NONE");
    assertThat(r.menuState()).isEqualTo("UNKNOWN");
    assertThat(r.menuPrice()).isNull();
    assertThat(r.lastSoldPrice()).isNull();
  }

  @Test
  void activeAndInactiveLinksPreferCurrent() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks())
        .thenReturn(
            List.of(
                link(1, "Parma", "Mains", "Parma", true),
                link(1, "Parma", "Mains", "Parma", false)));
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Parma", new BigDecimal("18.50"), new BigDecimal("6.00"), d)));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.priceSource()).isEqualTo("CURRENT");
    assertThat(r.menuState()).isEqualTo("UNKNOWN");
    assertThat(r.menuPrice()).isEqualByComparingTo("18.50");
    assertThat(r.lastSoldPrice()).isNull();
  }

  @Test
  void nullTradingDateIgnoredInLastSold() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks()).thenReturn(List.of(link(1, "Parma", "Mains", "Parma", false)));
    when(saleItems.currentSaleItemPrices())
        .thenReturn(
            List.of(
                new SaleItemPriceView(
                    "Parma", new BigDecimal("18.50"), new BigDecimal("6.00"), null),
                new SaleItemPriceView(
                    "Parma", new BigDecimal("20.00"), new BigDecimal("7.00"), d)));

    projector.recomputeAll();

    ResolvedRecipe r = captured().get(0);
    assertThat(r.lastSoldDate()).isEqualTo(d);
    assertThat(r.lastSoldPrice()).isEqualByComparingTo("20.00");
  }

  @Test
  void presentProductYieldsOnMenu() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks())
        .thenReturn(List.of(linkWithProduct(1, "Parma", "Mains", "Parma", 1)));
    when(saleItems.currentSaleItemPrices()).thenReturn(List.of());
    when(products.currentProducts())
        .thenReturn(List.of(product(1, new BigDecimal("20.00"), false)));

    projector.recomputeAll();

    assertThat(captured().get(0).menuState()).isEqualTo("ON_MENU");
  }

  @Test
  void retiredProductLeavesAvailabilityUnknown() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks())
        .thenReturn(List.of(linkWithProduct(1, "Parma", "Mains", "Parma", 1)));
    when(saleItems.currentSaleItemPrices()).thenReturn(List.of());
    when(products.currentProducts()).thenReturn(List.of());

    projector.recomputeAll();

    assertThat(captured().get(0).menuState()).isEqualTo("UNKNOWN");
  }

  @Test
  void unmatchedLinkYieldsUnknown() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks())
        .thenReturn(
            List.of(
                link(1, "Parma", "Mains", "Parma"), link(1, "Parma", "Mains", "Parma - Large")));
    when(saleItems.currentSaleItemPrices()).thenReturn(List.of());
    when(products.currentProducts()).thenReturn(List.of());

    projector.recomputeAll();

    assertThat(captured().get(0).menuState()).isEqualTo("UNKNOWN");
  }

  @Test
  void modifierProductNotOnMenu() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks())
        .thenReturn(List.of(linkWithProduct(1, "Parma", "Mains", "Parma", 2)));
    when(saleItems.currentSaleItemPrices()).thenReturn(List.of());
    when(products.currentProducts()).thenReturn(List.of(product(2, new BigDecimal("20.00"), true)));

    projector.recomputeAll();

    assertThat(captured().get(0).menuState()).isEqualTo("UNKNOWN");
  }

  @Test
  void zeroPriceProductCanBeListed() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks())
        .thenReturn(List.of(linkWithProduct(1, "Parma", "Mains", "Parma", 3)));
    when(saleItems.currentSaleItemPrices()).thenReturn(List.of());
    when(products.currentProducts()).thenReturn(List.of(product(3, BigDecimal.ZERO, false)));

    projector.recomputeAll();

    assertThat(captured().get(0).menuState()).isEqualTo("ON_MENU");
  }

  @Test
  void noLinksYieldsUnknown() {
    when(recipeCosts.currentRecipeCosts()).thenReturn(List.of(cost(1, "Parma", "6.00")));
    when(links.currentLinks()).thenReturn(List.of());
    when(saleItems.currentSaleItemPrices()).thenReturn(List.of());
    when(products.currentProducts()).thenReturn(List.of());

    projector.recomputeAll();

    assertThat(captured().get(0).menuState()).isEqualTo("UNKNOWN");
  }

  @SuppressWarnings("unchecked")
  private List<ResolvedRecipe> captured() {
    ArgumentCaptor<List<ResolvedRecipe>> captor = ArgumentCaptor.forClass((Class) List.class);
    verify(resolved).saveAll(captor.capture());
    return captor.getValue();
  }

  private static RecipeCostView cost(int recipeId, String name, String costPerPortion) {
    return new RecipeCostView(
        recipeId, name, new BigDecimal(costPerPortion), null, null, null, null);
  }

  private static SaleRecipeLinkView link(
      int recipeId, String recipeName, String folder, String description) {
    return link(recipeId, recipeName, folder, description, true);
  }

  private static SaleRecipeLinkView link(
      int recipeId, String recipeName, String folder, String description, Boolean isRecipeActive) {
    return new SaleRecipeLinkView(
        recipeId, recipeName, folder, description, isRecipeActive, recipeId, null);
  }

  private static SaleRecipeLinkView linkWithProduct(
      int recipeId,
      String recipeName,
      String folder,
      String description,
      Integer lightspeedProductId) {
    return new SaleRecipeLinkView(
        recipeId, recipeName, folder, description, true, recipeId, lightspeedProductId);
  }

  private static ProductView product(
      int productId, BigDecimal sellPriceIncTax, boolean isModifier) {
    return new ProductView(
        productId, "Product " + productId, "Category", sellPriceIncTax, isModifier, "Reporting");
  }
}
