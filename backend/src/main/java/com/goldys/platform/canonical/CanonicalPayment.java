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
 * One version of a source's payment-tender fact. Fact fields are immutable; a correction closes
 * this row and inserts a successor sharing the same logical identity.
 */
@Entity
@Table(name = "canonical_payment")
class CanonicalPayment extends BitemporalEntity {
  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "sale_number", nullable = false, updatable = false)
  private String saleNumber;

  @Column(name = "payment_type_code", updatable = false)
  private String paymentTypeCode;

  @Column(name = "payment_type_name", updatable = false)
  private String paymentTypeName;

  @Column(name = "payment_source_type", updatable = false)
  private String paymentSourceType;

  @Column(name = "lspay_payment_mode", updatable = false)
  private String lspayPaymentMode;

  @Column(name = "clearing_account", updatable = false)
  private String clearingAccount;

  @Column(name = "amount", nullable = false, updatable = false)
  private BigDecimal amount;

  @Column(name = "tip", nullable = false, updatable = false)
  private BigDecimal tip;

  @Column(name = "tendered", nullable = false, updatable = false)
  private BigDecimal tendered;

  @Column(name = "surcharge", nullable = false, updatable = false)
  private BigDecimal surcharge;

  @Column(name = "payment_count", nullable = false, updatable = false)
  private int paymentCount;

  @Column(name = "tip_count", nullable = false, updatable = false)
  private int tipCount;

  @Column(name = "reconciled", nullable = false, updatable = false)
  private String reconciled;

  @Column(name = "register_code", updatable = false)
  private String registerCode;

  @Column(name = "register_name", updatable = false)
  private String registerName;

  @Column(name = "staff_name", updatable = false)
  private String staffName;

  @Column(name = "staff_code", updatable = false)
  private String staffCode;

  @Column(name = "site_id", updatable = false)
  private String siteId;

  @Column(name = "customer_name", updatable = false)
  private String customerName;

  protected CanonicalPayment() {}

  private CanonicalPayment(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate tradingDate,
      String saleNumber,
      String paymentTypeCode,
      String paymentTypeName,
      String paymentSourceType,
      String lspayPaymentMode,
      String clearingAccount,
      BigDecimal amount,
      BigDecimal tip,
      BigDecimal tendered,
      BigDecimal surcharge,
      int paymentCount,
      int tipCount,
      String reconciled,
      String registerCode,
      String registerName,
      String staffName,
      String staffCode,
      String siteId,
      String customerName) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.tradingDate = tradingDate;
    this.saleNumber = saleNumber;
    this.paymentTypeCode = paymentTypeCode;
    this.paymentTypeName = paymentTypeName;
    this.paymentSourceType = paymentSourceType;
    this.lspayPaymentMode = lspayPaymentMode;
    this.clearingAccount = clearingAccount;
    this.amount = amount;
    this.tip = tip;
    this.tendered = tendered;
    this.surcharge = surcharge;
    this.paymentCount = paymentCount;
    this.tipCount = tipCount;
    this.reconciled = reconciled;
    this.registerCode = registerCode;
    this.registerName = registerName;
    this.staffName = staffName;
    this.staffCode = staffCode;
    this.siteId = siteId;
    this.customerName = customerName;
  }

  static CanonicalPayment create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      PaymentInput input) {
    return new CanonicalPayment(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        input.tradingDate(),
        input.saleNumber(),
        input.paymentTypeCode(),
        input.paymentTypeName(),
        input.paymentSourceType(),
        input.lspayPaymentMode(),
        input.clearingAccount(),
        input.amount(),
        input.tip(),
        input.tendered(),
        input.surcharge(),
        input.paymentCount(),
        input.tipCount(),
        input.reconciled(),
        input.registerCode(),
        input.registerName(),
        input.staffName(),
        input.staffCode(),
        input.siteId(),
        input.customerName());
  }

  boolean sameFact(PaymentInput input) {
    return Objects.equals(tradingDate, input.tradingDate())
        && Objects.equals(saleNumber, input.saleNumber())
        && Objects.equals(paymentTypeCode, input.paymentTypeCode())
        && Objects.equals(paymentTypeName, input.paymentTypeName())
        && Objects.equals(paymentSourceType, input.paymentSourceType())
        && Objects.equals(lspayPaymentMode, input.lspayPaymentMode())
        && Objects.equals(clearingAccount, input.clearingAccount())
        && Objects.equals(amount, input.amount())
        && Objects.equals(tip, input.tip())
        && Objects.equals(tendered, input.tendered())
        && Objects.equals(surcharge, input.surcharge())
        && paymentCount == input.paymentCount()
        && tipCount == input.tipCount()
        && Objects.equals(reconciled, input.reconciled())
        && Objects.equals(registerCode, input.registerCode())
        && Objects.equals(registerName, input.registerName())
        && Objects.equals(staffName, input.staffName())
        && Objects.equals(staffCode, input.staffCode())
        && Objects.equals(siteId, input.siteId())
        && Objects.equals(customerName, input.customerName());
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String saleNumber() {
    return saleNumber;
  }

  String paymentTypeName() {
    return paymentTypeName;
  }

  BigDecimal amount() {
    return amount;
  }

  BigDecimal tip() {
    return tip;
  }

  int paymentCount() {
    return paymentCount;
  }
}
