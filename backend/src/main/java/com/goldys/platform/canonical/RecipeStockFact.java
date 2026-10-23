package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeStock;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
record RecipeStockFact(
    @Column(name = "line_id") int lineId,
    @Column(name = "stock_id") Integer stockId,
    @Column(name = "stock_code", columnDefinition = "text") String stockCode,
    @Column(name = "description", columnDefinition = "text") String description,
    @Column(name = "supplier_name", columnDefinition = "text") String supplierName,
    @Column(name = "supplier_stock_code", columnDefinition = "text") String supplierStockCode,
    @Column(name = "quantity", precision = 28, scale = 12) BigDecimal quantity,
    @Column(name = "unit_id") Integer unitId,
    @Column(name = "unit", columnDefinition = "text") String unit,
    @Column(name = "stock_pack_size", precision = 28, scale = 12) BigDecimal stockPackSize,
    @Column(name = "stock_unit_id") Integer stockUnitId,
    @Column(name = "stock_unit", columnDefinition = "text") String stockUnit,
    @Column(name = "cost_per_pack", precision = 28, scale = 12) BigDecimal costPerPack,
    @Column(name = "cost", precision = 28, scale = 12) BigDecimal cost,
    @Column(name = "usable_percent", precision = 28, scale = 12) BigDecimal usablePercent,
    @Column(name = "wastage_percent", precision = 28, scale = 12) BigDecimal wastagePercent,
    @Column(name = "broken_link") Boolean brokenLink) {
  static RecipeStockFact from(RecipeStock value) {
    return new RecipeStockFact(
        value.lineId(),
        value.stockId(),
        value.stockCode(),
        value.description(),
        value.supplierName(),
        value.supplierStockCode(),
        value.quantity(),
        value.unitId(),
        value.unit(),
        value.stockPackSize(),
        value.stockUnitId(),
        value.stockUnit(),
        value.costPerPack(),
        value.cost(),
        value.usablePercent(),
        value.wastagePercent(),
        value.brokenLink());
  }

  RecipeStock toValue() {
    return new RecipeStock(
        lineId,
        stockId,
        stockCode,
        description,
        supplierName,
        supplierStockCode,
        quantity,
        unitId,
        unit,
        stockPackSize,
        stockUnitId,
        stockUnit,
        costPerPack,
        cost,
        usablePercent,
        wastagePercent,
        brokenLink);
  }
}
