package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeHeader;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
record RecipeHeaderFact(
    @Column(name = "recipe_id") int recipeId,
    @Column(name = "name", columnDefinition = "text") String name,
    @Column(name = "folder_id") Integer folderId,
    @Column(name = "active") Boolean active,
    @Column(name = "locked") Boolean locked,
    @Column(name = "portion_count", precision = 28, scale = 12) BigDecimal portionCount,
    @Column(name = "portion_size", precision = 28, scale = 12) BigDecimal portionSize,
    @Column(name = "unit_id") Integer unitId,
    @Column(name = "unit", columnDefinition = "text") String unit,
    @Column(name = "total_cost", precision = 28, scale = 12) BigDecimal totalCost,
    @Column(name = "cost_per_portion", precision = 28, scale = 12) BigDecimal costPerPortion,
    @Column(name = "cost_per_portion_including_labour", precision = 28, scale = 12)
        BigDecimal costPerPortionIncludingLabour,
    @Column(name = "labour_cost", precision = 28, scale = 12) BigDecimal labourCost,
    @Column(name = "selling_price_including_gst", precision = 28, scale = 12)
        BigDecimal sellingPriceIncludingGst,
    @Column(name = "revenue_excluding_gst", precision = 28, scale = 12)
        BigDecimal revenueExcludingGst,
    @Column(name = "gst", precision = 28, scale = 12) BigDecimal gst,
    @Column(name = "food_cost_percent", precision = 28, scale = 12) BigDecimal foodCostPercent,
    @Column(name = "gross_profit", precision = 28, scale = 12) BigDecimal grossProfit,
    @Column(name = "description", columnDefinition = "text") String description,
    @Column(name = "menu_description", columnDefinition = "text") String menuDescription,
    @Column(name = "hints", columnDefinition = "text") String hints,
    @Column(name = "preparation_minutes", precision = 28, scale = 12) BigDecimal preparationMinutes,
    @Column(name = "production_item") Boolean productionItem,
    @Column(name = "countable_on_stocktake") Boolean countableOnStocktake,
    @Column(name = "video_url", columnDefinition = "text") String videoUrl,
    @Column(name = "recipe_date", columnDefinition = "text") String recipeDate,
    @Column(name = "created_by_name", columnDefinition = "text") String createdByName,
    @Column(name = "difficulty_rating") Integer difficultyRating,
    @Column(name = "barcode", columnDefinition = "text") String barcode,
    @Column(name = "stock_category_id") Integer stockCategoryId,
    @Column(name = "food_cost_limit", precision = 28, scale = 12) BigDecimal foodCostLimit,
    @Column(name = "food_cost_limit_including_labour", precision = 28, scale = 12)
        BigDecimal foodCostLimitIncludingLabour) {
  static RecipeHeaderFact from(RecipeHeader value) {
    return new RecipeHeaderFact(
        value.recipeId(),
        value.name(),
        value.folderId(),
        value.active(),
        value.locked(),
        value.portionCount(),
        value.portionSize(),
        value.unitId(),
        value.unit(),
        value.totalCost(),
        value.costPerPortion(),
        value.costPerPortionIncludingLabour(),
        value.labourCost(),
        value.sellingPriceIncludingGst(),
        value.revenueExcludingGst(),
        value.gst(),
        value.foodCostPercent(),
        value.grossProfit(),
        value.description(),
        value.menuDescription(),
        value.hints(),
        value.preparationMinutes(),
        value.productionItem(),
        value.countableOnStocktake(),
        value.videoUrl(),
        value.recipeDate(),
        value.createdByName(),
        value.difficultyRating(),
        value.barcode(),
        value.stockCategoryId(),
        value.foodCostLimit(),
        value.foodCostLimitIncludingLabour());
  }

  RecipeHeader toValue() {
    return new RecipeHeader(
        recipeId,
        name,
        folderId,
        active,
        locked,
        portionCount,
        portionSize,
        unitId,
        unit,
        totalCost,
        costPerPortion,
        costPerPortionIncludingLabour,
        labourCost,
        sellingPriceIncludingGst,
        revenueExcludingGst,
        gst,
        foodCostPercent,
        grossProfit,
        description,
        menuDescription,
        hints,
        preparationMinutes,
        productionItem,
        countableOnStocktake,
        videoUrl,
        recipeDate,
        createdByName,
        difficultyRating,
        barcode,
        stockCategoryId,
        foodCostLimit,
        foodCostLimitIncludingLabour);
  }
}
