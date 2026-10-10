package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalBrowseQuery;
import com.goldys.platform.ingestion.RawRecordBrowseQuery;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.EntityDescriptor;
import com.goldys.platform.semantic.GenericRow;
import com.goldys.platform.semantic.RawFilter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class DataExplorerQueryImplTest {

  private final RawRecordBrowseQuery raw = mock(RawRecordBrowseQuery.class);
  private final CanonicalBrowseQuery canonical = mock(CanonicalBrowseQuery.class);
  private final ResolvedDailySalesRepository dailySales = mock(ResolvedDailySalesRepository.class);
  private final ResolvedProductSalesRepository productSales =
      mock(ResolvedProductSalesRepository.class);
  private final ResolvedReservationDayRepository reservations =
      mock(ResolvedReservationDayRepository.class);
  private final ResolvedLabourDayRepository labour = mock(ResolvedLabourDayRepository.class);
  private final ResolvedInventoryDayRepository inventory =
      mock(ResolvedInventoryDayRepository.class);
  private final ResolvedPaymentDayRepository payments = mock(ResolvedPaymentDayRepository.class);
  private final ResolvedDeletedSaleDayRepository deletedSales =
      mock(ResolvedDeletedSaleDayRepository.class);
  private final ResolvedSaleItemDayRepository saleItems = mock(ResolvedSaleItemDayRepository.class);

  private final DataExplorerQueryImpl impl =
      new DataExplorerQueryImpl(
          raw,
          canonical,
          dailySales,
          productSales,
          reservations,
          labour,
          inventory,
          payments,
          deletedSales,
          saleItems);

  @Test
  void canonicalEntitiesDelegatesToTheFacade() {
    List<EntityDescriptor> descriptors =
        List.of(new EntityDescriptor("daily_sales", "Daily sales", false));
    when(canonical.entities()).thenReturn(descriptors);

    assertThat(impl.canonicalEntities()).isEqualTo(descriptors);
  }

  @Test
  void resolvedDomainsEnumeratesEight() {
    assertThat(impl.resolvedDomains()).hasSize(8);
    assertThat(impl.resolvedDomains().stream().map(EntityDescriptor::id))
        .containsExactly(
            "resolved_daily_sales",
            "resolved_product_sales",
            "resolved_reservation_day",
            "resolved_labour_day",
            "resolved_inventory_day",
            "resolved_payment_day",
            "resolved_deleted_sale_day",
            "resolved_sale_item_day");
  }

  @Test
  void listRawDelegatesToTheRawFacade() {
    RawFilter filter = new RawFilter("CTB", null, null, null, null);
    impl.listRaw(filter, 0, 10);

    verify(raw).list(filter, 0, 10);
  }

  @Test
  void canonicalRowsDelegatesToTheFacade() {
    impl.canonicalRows("invoice", 0, 10);

    verify(canonical).listRows("invoice", 0, 10);
  }

  @Test
  void resolvedRowsMapsDailySalesToGenericRow() {
    ResolvedDailySales row =
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13),
            bd("100.0000"),
            bd("91.0000"),
            bd("9.0000"),
            "agreed",
            "CTB",
            false,
            Instant.parse("2026-09-13T00:00:00Z"));
    when(dailySales.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row)));

    DataPage<GenericRow> page = impl.resolvedRows("resolved_daily_sales", 0, 10);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).columns())
        .containsEntry("trading_date", "2026-09-13")
        .containsEntry("total_sales", "100.0000")
        .containsEntry("resolution_type", "agreed")
        .containsEntry("authoritative_source", "CTB");
  }

  @Test
  void unknownResolvedDomainThrows() {
    assertThatThrownBy(() -> impl.resolvedRows("nope", 0, 10))
        .isInstanceOf(java.util.NoSuchElementException.class)
        .hasMessageContaining("nope");
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
