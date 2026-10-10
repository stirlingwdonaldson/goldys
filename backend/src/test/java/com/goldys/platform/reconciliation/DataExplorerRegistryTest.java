package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.EntityDescriptor;
import com.goldys.platform.semantic.GenericRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Registry tests for the three Lightspeed resolved domains: payments, deleted sales, sale items.
 */
class DataExplorerRegistryTest {

  @Test
  void resolvedDomainsIncludesTheThreeNewDomains() {
    List<EntityDescriptor> domains =
        new DataExplorerQueryImpl(null, null, null, null, null, null, null, null, null, null)
            .resolvedDomains();

    assertThat(domains)
        .contains(
            new EntityDescriptor("resolved_payment_day", "Payments", false),
            new EntityDescriptor("resolved_deleted_sale_day", "Deleted orders", false),
            new EntityDescriptor("resolved_sale_item_day", "Sale items", false));
  }

  @Test
  void paymentRowMapsColumns() {
    ResolvedPaymentDay day =
        new ResolvedPaymentDay(
            LocalDate.of(2026, 9, 13),
            "card",
            bd("100.0000"),
            bd("10.0000"),
            5L,
            "single",
            "LIGHTSPEED",
            false,
            Instant.parse("2026-09-13T00:00:00Z"));

    GenericRow row = DataExplorerQueryImpl.paymentRow(day);

    assertThat(row.columns())
        .containsEntry("trading_date", "2026-09-13")
        .containsEntry("payment_type_name", "card")
        .containsEntry("amount", "100.0000")
        .containsEntry("tip", "10.0000")
        .containsEntry("payment_count", "5")
        .containsEntry("resolution_type", "single")
        .containsEntry("authoritative_source", "LIGHTSPEED")
        .containsEntry("has_conflict", "false")
        .containsEntry("resolved_at", "2026-09-13T00:00:00Z");
  }

  @Test
  void deletedSaleRowMapsColumns() {
    ResolvedDeletedSaleDay day =
        new ResolvedDeletedSaleDay(
            LocalDate.of(2026, 9, 13),
            3L,
            bd("150.0000"),
            bd("13.6400"),
            "single",
            "LIGHTSPEED",
            false,
            Instant.parse("2026-09-13T00:00:00Z"));

    GenericRow row = DataExplorerQueryImpl.deletedSaleRow(day);

    assertThat(row.id()).isEqualTo("2026-09-13");
    assertThat(row.columns())
        .containsEntry("trading_date", "2026-09-13")
        .containsEntry("deleted_count", "3")
        .containsEntry("total_inc_tax", "150.0000")
        .containsEntry("total_tax", "13.6400")
        .containsEntry("resolution_type", "single")
        .containsEntry("authoritative_source", "LIGHTSPEED")
        .containsEntry("has_conflict", "false")
        .containsEntry("resolved_at", "2026-09-13T00:00:00Z");
  }

  @Test
  void saleItemRowMapsColumns() {
    ResolvedSaleItemDay day =
        new ResolvedSaleItemDay(
            LocalDate.of(2026, 9, 13),
            "Food",
            bd("12.0000"),
            bd("240.0000"),
            "single",
            "LIGHTSPEED",
            false,
            Instant.parse("2026-09-13T00:00:00Z"));

    GenericRow row = DataExplorerQueryImpl.saleItemRow(day);

    assertThat(row.id()).isEqualTo("2026-09-13|Food");
    assertThat(row.columns())
        .containsEntry("trading_date", "2026-09-13")
        .containsEntry("category_name", "Food")
        .containsEntry("quantity", "12.0000")
        .containsEntry("amount", "240.0000")
        .containsEntry("resolution_type", "single")
        .containsEntry("authoritative_source", "LIGHTSPEED")
        .containsEntry("has_conflict", "false")
        .containsEntry("resolved_at", "2026-09-13T00:00:00Z");
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
