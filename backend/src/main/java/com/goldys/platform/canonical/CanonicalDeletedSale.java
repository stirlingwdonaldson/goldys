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
 * One version of a source's deleted-order fact. Fact fields are immutable; a correction closes this
 * row and inserts a successor sharing the same logical identity (the Lightspeed sale number).
 */
@Entity
@Table(name = "canonical_deleted_sale")
class CanonicalDeletedSale extends BitemporalEntity {
  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "sale_number", nullable = false, updatable = false)
  private String saleNumber;

  @Column(name = "order_type", updatable = false)
  private String orderType;

  @Column(name = "note", updatable = false)
  private String note;

  @Column(name = "total_inc_tax", nullable = false, updatable = false)
  private BigDecimal totalIncTax;

  @Column(name = "total_ex_tax", nullable = false, updatable = false)
  private BigDecimal totalExTax;

  @Column(name = "total_tax", nullable = false, updatable = false)
  private BigDecimal totalTax;

  @Column(name = "total_cost", updatable = false)
  private BigDecimal totalCost;

  @Column(name = "opened_register_code", updatable = false)
  private String openedRegisterCode;

  @Column(name = "opened_register_name", updatable = false)
  private String openedRegisterName;

  @Column(name = "deleted_register_code", updatable = false)
  private String deletedRegisterCode;

  @Column(name = "deleted_register_name", updatable = false)
  private String deletedRegisterName;

  @Column(name = "staff_name", updatable = false)
  private String staffName;

  @Column(name = "staff_code", updatable = false)
  private String staffCode;

  @Column(name = "deleted_by_staff_name", updatable = false)
  private String deletedByStaffName;

  @Column(name = "deleted_by_staff_code", updatable = false)
  private String deletedByStaffCode;

  @Column(name = "table_number", updatable = false)
  private String tableNumber;

  @Column(name = "site_id", updatable = false)
  private String siteId;

  @Column(name = "customer_name", updatable = false)
  private String customerName;

  protected CanonicalDeletedSale() {}

  private CanonicalDeletedSale(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      DeletedSaleInput input) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.tradingDate = input.tradingDate();
    this.saleNumber = input.saleNumber();
    this.orderType = input.orderType();
    this.note = input.note();
    this.totalIncTax = input.totalIncTax();
    this.totalExTax = input.totalExTax();
    this.totalTax = input.totalTax();
    this.totalCost = input.totalCost();
    this.openedRegisterCode = input.openedRegisterCode();
    this.openedRegisterName = input.openedRegisterName();
    this.deletedRegisterCode = input.deletedRegisterCode();
    this.deletedRegisterName = input.deletedRegisterName();
    this.staffName = input.staffName();
    this.staffCode = input.staffCode();
    this.deletedByStaffName = input.deletedByStaffName();
    this.deletedByStaffCode = input.deletedByStaffCode();
    this.tableNumber = input.tableNumber();
    this.siteId = input.siteId();
    this.customerName = input.customerName();
  }

  static CanonicalDeletedSale create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      DeletedSaleInput input) {
    return new CanonicalDeletedSale(
        logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt, input);
  }

  boolean sameFact(DeletedSaleInput input) {
    return Objects.equals(tradingDate, input.tradingDate())
        && Objects.equals(saleNumber, input.saleNumber())
        && Objects.equals(orderType, input.orderType())
        && Objects.equals(note, input.note())
        && Objects.equals(totalIncTax, input.totalIncTax())
        && Objects.equals(totalExTax, input.totalExTax())
        && Objects.equals(totalTax, input.totalTax())
        && Objects.equals(totalCost, input.totalCost())
        && Objects.equals(openedRegisterCode, input.openedRegisterCode())
        && Objects.equals(openedRegisterName, input.openedRegisterName())
        && Objects.equals(deletedRegisterCode, input.deletedRegisterCode())
        && Objects.equals(deletedRegisterName, input.deletedRegisterName())
        && Objects.equals(staffName, input.staffName())
        && Objects.equals(staffCode, input.staffCode())
        && Objects.equals(deletedByStaffName, input.deletedByStaffName())
        && Objects.equals(deletedByStaffCode, input.deletedByStaffCode())
        && Objects.equals(tableNumber, input.tableNumber())
        && Objects.equals(siteId, input.siteId())
        && Objects.equals(customerName, input.customerName());
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String saleNumber() {
    return saleNumber;
  }
}
