package com.goldys.platform.canonical;

/**
 * A current sale item's display name and POS category, used by the categorisation feature to build
 * each product's feature text. The name is the original, un-normalized menu-item text (the {@code
 * product_name_key} is derived from it via {@link ProductNameKey#normalize}).
 */
public record SaleItemIdentityView(String itemName, String categoryName) {}
