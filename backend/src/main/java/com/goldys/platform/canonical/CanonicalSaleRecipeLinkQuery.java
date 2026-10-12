package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical sale-item→recipe links, so modules outside this package
 * never touch the package-private entity or repository directly.
 */
@Service
public class CanonicalSaleRecipeLinkQuery {
  private final CanonicalSaleRecipeLinkRepository repository;

  public CanonicalSaleRecipeLinkQuery(CanonicalSaleRecipeLinkRepository repository) {
    this.repository = repository;
  }

  /** All current links, mapped to views. */
  public List<SaleRecipeLinkView> currentLinks() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  private SaleRecipeLinkView toView(CanonicalSaleRecipeLink l) {
    return new SaleRecipeLinkView(
        l.recipeId(),
        l.recipeName(),
        l.recipeFolderName(),
        l.saleItemStockDescription(),
        l.isRecipeActive());
  }
}
