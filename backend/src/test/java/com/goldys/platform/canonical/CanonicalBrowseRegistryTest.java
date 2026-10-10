package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.EntityDescriptor;
import com.goldys.platform.semantic.GenericRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CanonicalBrowseRegistryTest {

  @Test
  void entitiesListsPaymentsAndDeletedSalesAndUnplacesSaleItem() {
    var browse = browseQuery();

    assertThat(browse.entities()).hasSize(12);
    assertThat(descriptor(browse.entities(), "payment").label()).isEqualTo("Payments");
    assertThat(descriptor(browse.entities(), "payment").placeholder()).isFalse();
    assertThat(descriptor(browse.entities(), "deleted_sale").label()).isEqualTo("Deleted orders");
    assertThat(descriptor(browse.entities(), "deleted_sale").placeholder()).isFalse();
    assertThat(descriptor(browse.entities(), "sale_item").placeholder()).isFalse();
    assertThat(descriptor(browse.entities(), "shift").placeholder()).isTrue();
  }

  @Test
  void paymentColumnsMapsFacts() {
    Map<String, String> columns = CanonicalBrowseQuery.paymentColumns(paymentEntity());

    assertThat(columns)
        .containsEntry("trading_date", "2026-09-13")
        .containsEntry("sale_number", "sale-123")
        .containsEntry("payment_type_name", "Card")
        .containsEntry("amount", "42.50")
        .containsEntry("tip", "3.00")
        .containsEntry("payment_count", "1");
  }

  @Test
  void deletedSaleColumnsMapsFacts() {
    Map<String, String> columns = CanonicalBrowseQuery.deletedSaleColumns(deletedSaleEntity());

    assertThat(columns)
        .containsEntry("trading_date", "2026-09-13")
        .containsEntry("sale_number", "sale-999")
        .containsEntry("total_inc_tax", "120.00")
        .containsEntry("total_tax", "10.91");
  }

  @Test
  void saleItemColumnsMapsLineDetailFacts() {
    Map<String, String> columns = CanonicalBrowseQuery.saleItemColumns(saleItemEntity());

    assertThat(columns)
        .containsEntry("item_name", "Cheeseburger")
        .containsEntry("quantity_sold", "2")
        .containsEntry("amount", "15.00")
        .containsEntry("sale_number", "sale-123")
        .containsEntry("category_name", "Burgers")
        .containsEntry("product_number", "PROD-1")
        .containsEntry("sku", "SKU-1")
        .containsEntry("sold_price_inc_tax", "7.50")
        .containsEntry("total_tax", "1.36")
        .containsEntry("cost_inc_tax", "4.00")
        .containsEntry("order_type", "Dine In")
        .containsEntry("sale_type", "Food")
        .containsEntry("staff_name", "A. Staff")
        .containsEntry("register_name", "Front Register")
        .containsEntry("table_number", "T12");
  }

  @Test
  void listRowsServesPaymentAndDeletedSale() {
    CanonicalPaymentRepository payments = mock(CanonicalPaymentRepository.class);
    CanonicalDeletedSaleRepository deletedSales = mock(CanonicalDeletedSaleRepository.class);
    var browse = browseQuery(payments, deletedSales);
    when(payments.findAll(any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(paymentEntity())));
    when(deletedSales.findAll(any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(deletedSaleEntity())));

    DataPage<GenericRow> paymentPage = browse.listRows("payment", 0, 10);
    DataPage<GenericRow> deletedSalePage = browse.listRows("deleted_sale", 0, 10);

    assertThat(paymentPage.total()).isEqualTo(1);
    assertThat(paymentPage.items().get(0).columns()).containsEntry("payment_type_name", "Card");
    assertThat(deletedSalePage.total()).isEqualTo(1);
    assertThat(deletedSalePage.items().get(0).columns()).containsEntry("total_inc_tax", "120.00");
  }

  private static CanonicalBrowseQuery browseQuery() {
    return browseQuery(
        mock(CanonicalPaymentRepository.class), mock(CanonicalDeletedSaleRepository.class));
  }

  private static CanonicalBrowseQuery browseQuery(
      CanonicalPaymentRepository payments, CanonicalDeletedSaleRepository deletedSales) {
    return new CanonicalBrowseQuery(
        mock(CanonicalDailySalesRepository.class),
        mock(CanonicalProductSalesRepository.class),
        mock(CanonicalReservationRepository.class),
        mock(CanonicalInvoiceRepository.class),
        mock(CanonicalInvoiceLineRepository.class),
        mock(CanonicalLabourEntryRepository.class),
        mock(CanonicalStockCountRepository.class),
        mock(CanonicalWastageRepository.class),
        mock(CanonicalSaleItemRepository.class),
        mock(CanonicalShiftRepository.class),
        payments,
        deletedSales);
  }

  private static EntityDescriptor descriptor(Iterable<EntityDescriptor> entities, String id) {
    for (EntityDescriptor d : entities) {
      if (d.id().equals(id)) {
        return d;
      }
    }
    throw new AssertionError("no descriptor " + id);
  }

  private static CanonicalPayment paymentEntity() {
    return CanonicalPayment.create(
        UUID.randomUUID(),
        "LSPAY",
        "sale-123",
        UUID.randomUUID(),
        Instant.parse("2026-09-13T00:00:00Z"),
        Instant.parse("2026-09-13T01:00:00Z"),
        new PaymentInput(
            "LSPAY",
            LocalDate.of(2026, 9, 13),
            "sale-123",
            "CARD",
            "Card",
            "TERMINAL",
            "EFTPOS",
            null,
            new BigDecimal("42.50"),
            new BigDecimal("3.00"),
            new BigDecimal("45.50"),
            new BigDecimal("0.00"),
            1,
            1,
            "Y",
            "REG-1",
            "Front Register",
            "A. Staff",
            "STAFF-1",
            "SITE-1",
            "Walk In",
            UUID.randomUUID()));
  }

  private static CanonicalDeletedSale deletedSaleEntity() {
    return CanonicalDeletedSale.create(
        UUID.randomUUID(),
        "LSPAY",
        "sale-999",
        UUID.randomUUID(),
        Instant.parse("2026-09-13T00:00:00Z"),
        Instant.parse("2026-09-13T01:00:00Z"),
        new DeletedSaleInput(
            "LSPAY",
            LocalDate.of(2026, 9, 13),
            "sale-999",
            "Dine In",
            "test note",
            new BigDecimal("120.00"),
            new BigDecimal("109.09"),
            new BigDecimal("10.91"),
            new BigDecimal("40.00"),
            null,
            null,
            "REG-1",
            "Front Register",
            "A. Staff",
            "STAFF-1",
            "M. Manager",
            "MGR-1",
            "T12",
            "SITE-1",
            "Walk In",
            UUID.randomUUID()));
  }

  private static CanonicalSaleItem saleItemEntity() {
    return CanonicalSaleItem.create(
        UUID.randomUUID(),
        "LSPAY",
        "line-1",
        UUID.randomUUID(),
        Instant.parse("2026-09-13T00:00:00Z"),
        Instant.parse("2026-09-13T01:00:00Z"),
        new SaleItemInput(
            "LSPAY",
            "line-1",
            LocalDate.of(2026, 9, 13),
            "sale-123",
            "Cheeseburger",
            "PROD-1",
            "SKU-1",
            "Burgers",
            new BigDecimal("2"),
            new BigDecimal("15.00"),
            new BigDecimal("7.50"),
            new BigDecimal("1.36"),
            new BigDecimal("4.00"),
            "Dine In",
            "Food",
            "A. Staff",
            "Front Register",
            "T12",
            UUID.randomUUID()));
  }
}
