package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichedInvoiceLine;
import com.goldys.platform.canonical.InvoiceMetadataView;
import com.goldys.platform.canonical.InvoicePdfLinkService;
import com.goldys.platform.semantic.DataPage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InvoiceReportingServiceTest {

  private final CanonicalInvoiceQuery invoices = mock(CanonicalInvoiceQuery.class);
  private final CanonicalInventoryQuery inventory = mock(CanonicalInventoryQuery.class);
  private final InvoicePdfLinkService pdfLinks = mock(InvoicePdfLinkService.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final InvoiceReportingService service =
      new InvoiceReportingService(invoices, inventory, pdfLinks, permissions);

  private static UserRole role() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  @Test
  void listAuthorizesFiltersSortsAndFlagsPdf() {
    when(invoices.currentInvoices())
        .thenReturn(
            List.of(
                invoice("A. Foods", "INV-2", "2026-09-02", "20.00"),
                invoice("A. Foods", "INV-1", "2026-09-01", "10.00"),
                invoice("B. Foods", "INV-3", "2026-09-03", "30.00")));
    when(pdfLinks.linkedInvoiceNumbers()).thenReturn(Set.of("INV-1"));

    DataPage<InvoiceReportingService.InvoiceSummary> page =
        service.list(
            role(), "A. Foods", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0, 200);

    verify(permissions).require(role(), new ResourceKey("inventory.cost"), PermissionAction.READ);
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).hasSize(2);
    assertThat(page.items().get(0).invoiceNumber()).isEqualTo("INV-2");
    assertThat(page.items().get(0).hasPdf()).isFalse();
    assertThat(page.items().get(1).invoiceNumber()).isEqualTo("INV-1");
    assertThat(page.items().get(1).hasPdf()).isTrue();
  }

  @Test
  void listFiltersInvoiceDateInclusively() {
    when(invoices.currentInvoices())
        .thenReturn(
            List.of(
                invoice("A. Foods", "INV-1", "2026-08-31", "10.00"),
                invoice("A. Foods", "INV-2", "2026-09-01", "20.00"),
                invoice("A. Foods", "INV-3", "2026-09-30", "30.00"),
                invoice("A. Foods", "INV-4", "2026-10-01", "40.00")));
    when(pdfLinks.linkedInvoiceNumbers()).thenReturn(Set.of());

    DataPage<InvoiceReportingService.InvoiceSummary> page =
        service.list(role(), null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0, 200);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items())
        .extracting(InvoiceReportingService.InvoiceSummary::invoiceNumber)
        .containsExactly("INV-3", "INV-2");
  }

  @Test
  void listWithoutDateBoundsReturnsAllInvoices() {
    when(invoices.currentInvoices())
        .thenReturn(
            List.of(
                invoice("A. Foods", "INV-1", "2026-09-01", "10.00"),
                invoice("A. Foods", "INV-2", "2026-08-01", "20.00"),
                invoice("B. Foods", "INV-3", "2026-10-01", "30.00")));
    when(pdfLinks.linkedInvoiceNumbers()).thenReturn(Set.of());

    DataPage<InvoiceReportingService.InvoiceSummary> page =
        service.list(role(), null, null, null, 0, 200);

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.items())
        .extracting(InvoiceReportingService.InvoiceSummary::invoiceNumber)
        .containsExactly("INV-3", "INV-1", "INV-2");
  }

  @Test
  void listPaginatesInMemory() {
    when(invoices.currentInvoices())
        .thenReturn(
            List.of(
                invoice("A. Foods", "INV-1", "2026-09-01", "10.00"),
                invoice("A. Foods", "INV-2", "2026-09-02", "20.00"),
                invoice("A. Foods", "INV-3", "2026-09-03", "30.00")));
    when(pdfLinks.linkedInvoiceNumbers()).thenReturn(Set.of());

    DataPage<InvoiceReportingService.InvoiceSummary> page =
        service.list(role(), null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 1, 1);

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).invoiceNumber()).isEqualTo("INV-2");
  }

  @Test
  void listWithHugePageReturnsEmptyPageNotThrow() {
    when(invoices.currentInvoices())
        .thenReturn(
            List.of(
                invoice("A. Foods", "INV-1", "2026-09-01", "10.00"),
                invoice("A. Foods", "INV-2", "2026-09-02", "20.00")));
    when(pdfLinks.linkedInvoiceNumbers()).thenReturn(Set.of());

    DataPage<InvoiceReportingService.InvoiceSummary> page =
        service.list(role(), null, null, null, Integer.MAX_VALUE / 2, 200);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).isEmpty();
  }

  @Test
  void detailReturnsHeaderAndLinesSortedByLineTotalDesc() {
    when(invoices.currentInvoices())
        .thenReturn(List.of(invoice("A. Foods", "INV-1", "2026-09-01", "30.00")));
    when(inventory.currentEnrichedLines())
        .thenReturn(List.of(line("INV-1", "10.00", "cheap"), line("INV-1", "20.00", "pricey")));
    when(pdfLinks.linkedInvoiceNumbers()).thenReturn(Set.of("INV-1"));

    InvoiceReportingService.InvoiceDetail detail = service.detail(role(), "INV-1");

    verify(permissions).require(role(), new ResourceKey("inventory.cost"), PermissionAction.READ);
    assertThat(detail.supplierName()).isEqualTo("A. Foods");
    assertThat(detail.invoiceNumber()).isEqualTo("INV-1");
    assertThat(detail.hasPdf()).isTrue();
    assertThat(detail.lines()).hasSize(2);
    assertThat(detail.lines().get(0).productNameKey()).isEqualTo("pricey");
    assertThat(detail.lines().get(0).lineTotal()).isEqualByComparingTo("20.00");
  }

  @Test
  void detailThrowsWhenInvoiceNumberHasNoCurrentHeader() {
    when(invoices.currentInvoices()).thenReturn(List.of());

    assertThatThrownBy(() -> service.detail(role(), "NOPE"))
        .isInstanceOf(java.util.NoSuchElementException.class);
  }

  private static InvoiceMetadataView invoice(
      String supplier, String number, String date, String total) {
    return new InvoiceMetadataView(
        supplier, number, LocalDate.parse(date), null, new BigDecimal(total), null, null);
  }

  private static EnrichedInvoiceLine line(String invoice, String lineTotal, String key) {
    return new EnrichedInvoiceLine(
        invoice,
        LocalDate.of(2026, 9, 1),
        key,
        null,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        new BigDecimal(lineTotal),
        null,
        null,
        null,
        null);
  }
}
