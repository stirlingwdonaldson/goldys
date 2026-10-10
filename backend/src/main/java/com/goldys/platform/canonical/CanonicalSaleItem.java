package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a canonical sale line item (a Lightspeed receipt line).
 *
 * <p>Fact fields are immutable; a correction closes this row and inserts a successor sharing the
 * same logical identity (the receipt line id). {@code amount} is the line total inc. tax.
 */
@Entity
@Table(name = "canonical_sale_item")
class CanonicalSaleItem extends BitemporalEntity {
  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "sale_number", updatable = false)
  private String saleNumber;

  @Column(name = "item_name", updatable = false, length = 255)
  private String itemName;

  @Column(name = "product_number", updatable = false)
  private String productNumber;

  @Column(name = "sku", updatable = false)
  private String sku;

  @Column(name = "category_name", updatable = false)
  private String categoryName;

  @Column(name = "quantity_sold", updatable = false)
  private Integer quantitySold;

  @Column(name = "amount", updatable = false, precision = 38, scale = 2)
  private BigDecimal amount;

  @Column(name = "sold_price_inc_tax", updatable = false)
  private BigDecimal soldPriceIncTax;

  @Column(name = "total_tax", updatable = false)
  private BigDecimal totalTax;

  @Column(name = "cost_inc_tax", updatable = false)
  private BigDecimal costIncTax;

  @Column(name = "order_type", updatable = false)
  private String orderType;

  @Column(name = "sale_type", updatable = false)
  private String saleType;

  @Column(name = "staff_name", updatable = false)
  private String staffName;

  @Column(name = "register_name", updatable = false)
  private String registerName;

  @Column(name = "table_number", updatable = false)
  private String tableNumber;

  protected CanonicalSaleItem() {}

  private CanonicalSaleItem(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      SaleItemInput input) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.tradingDate = input.tradingDate();
    this.saleNumber = input.saleNumber();
    this.itemName = input.itemName();
    this.productNumber = input.productNumber();
    this.sku = input.sku();
    this.categoryName = input.categoryName();
    this.quantitySold = input.quantitySold();
    this.amount = input.amount();
    this.soldPriceIncTax = input.soldPriceIncTax();
    this.totalTax = input.totalTax();
    this.costIncTax = input.costIncTax();
    this.orderType = input.orderType();
    this.saleType = input.saleType();
    this.staffName = input.staffName();
    this.registerName = input.registerName();
    this.tableNumber = input.tableNumber();
  }

  static CanonicalSaleItem create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      SaleItemInput input) {
    return new CanonicalSaleItem(
        logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt, input);
  }

  boolean sameFact(SaleItemInput input) {
    return Objects.equals(tradingDate, input.tradingDate())
        && Objects.equals(saleNumber, input.saleNumber())
        && Objects.equals(itemName, input.itemName())
        && Objects.equals(productNumber, input.productNumber())
        && Objects.equals(sku, input.sku())
        && Objects.equals(categoryName, input.categoryName())
        && Objects.equals(quantitySold, input.quantitySold())
        && sameAmount(amount, input.amount())
        && sameAmount(soldPriceIncTax, input.soldPriceIncTax())
        && sameAmount(totalTax, input.totalTax())
        && sameAmount(costIncTax, input.costIncTax())
        && Objects.equals(orderType, input.orderType())
        && Objects.equals(saleType, input.saleType())
        && Objects.equals(staffName, input.staffName())
        && Objects.equals(registerName, input.registerName())
        && Objects.equals(tableNumber, input.tableNumber());
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String itemName() {
    return itemName;
  }

  Integer quantitySold() {
    return quantitySold;
  }

  BigDecimal amount() {
    return amount;
  }
}
