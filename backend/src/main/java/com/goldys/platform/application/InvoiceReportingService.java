package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichedInvoiceLine;
import com.goldys.platform.canonical.InvoiceMetadataView;
import com.goldys.platform.canonical.InvoicePdfLinkService;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.LineGraphNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * The Invoices screen's read model: a paged, filterable browse of current invoice headers plus a
 * per-invoice detail with its lines. Gated on the same {@code inventory.cost} READ permission as
 * the rest of the inventory surface, and never touched directly by the REST controller.
 */
@Service
public class InvoiceReportingService {

  private static final ResourceKey RESOURCE = new ResourceKey("inventory.cost");
  private static final int MAX_PAGE_SIZE = 200;

  private final CanonicalInvoiceQuery invoices;
  private final CanonicalInventoryQuery inventory;
  private final InvoicePdfLinkService pdfLinks;
  private final PermissionService permissions;

  public InvoiceReportingService(
      CanonicalInvoiceQuery invoices,
      CanonicalInventoryQuery inventory,
      InvoicePdfLinkService pdfLinks,
      PermissionService permissions) {
    this.invoices = invoices;
    this.inventory = inventory;
    this.pdfLinks = pdfLinks;
    this.permissions = permissions;
  }

  /**
   * Paged, filterable listing of current invoice headers. Supplier is an exact match (null matches
   * any); the invoice date must fall in the inclusive {@code [from, to]} range. Newest date first,
   * then invoice number ascending, paginated in memory.
   */
  public DataPage<InvoiceSummary> list(
      UserRole role, String supplier, LocalDate from, LocalDate to, int page, int size) {
    permissions.require(role, RESOURCE, PermissionAction.READ);

    List<InvoiceMetadataView> summaries =
        invoices.currentInvoices().stream()
            .filter(i -> supplier == null || supplier.equals(i.supplierName()))
            .filter(i -> inRange(i.invoiceDate(), from, to))
            .sorted(
                Comparator.comparing(InvoiceMetadataView::invoiceDate)
                    .reversed()
                    .thenComparing(InvoiceMetadataView::invoiceNumber))
            .toList();

    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int safePage = Math.max(page, 0);
    int fromIndex = safePage * safeSize;
    List<InvoiceMetadataView> items =
        fromIndex < 0 || fromIndex >= summaries.size()
            ? List.of()
            : summaries.subList(fromIndex, Math.min(fromIndex + safeSize, summaries.size()));
    return new DataPage<>(
        items.stream().map(this::toSummary).toList(), summaries.size(), safePage, safeSize);
  }

  /** One invoice's header plus its lines (newest line total first), or {@code NOT_FOUND}. */
  public InvoiceDetail detail(UserRole role, String invoiceNumber) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    invoices.requireUnambiguousNumber(invoiceNumber);
    java.util.UUID id =
        invoices
            .invoiceLogicalIdForInvoiceNumber(invoiceNumber)
            .orElseThrow(() -> new NoSuchElementException("Unknown invoice"));
    return detailById(role, id);
  }

  public InvoiceDetail detailById(UserRole role, java.util.UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    InvoiceMetadataView header =
        invoices
            .currentInvoice(id)
            .orElseThrow(() -> new NoSuchElementException("Unknown invoice"));
    List<LineGraphNode> lines =
        inventory.enrichedLinesForInvoice(id).stream()
            .sorted(
                Comparator.comparing(
                    EnrichedInvoiceLine::lineTotal,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .map(
                l ->
                    new LineGraphNode(
                        l.productNameKey(),
                        l.stockCode(),
                        l.quantity(),
                        l.unitCost(),
                        l.lineTotal(),
                        l.uom(),
                        l.lineTotalIncTax(),
                        l.lineGstAmount(),
                        l.lineTaxRate(),
                        l.incGst()))
            .toList();
    String status = pdfLinks.status(id);
    BigDecimal lineSubtotal =
        lines.stream()
            .map(LineGraphNode::lineTotal)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal::add)
            .orElse(null);
    BigDecimal difference =
        header.amountExTax() == null || lineSubtotal == null
            ? null
            : header.amountExTax().subtract(lineSubtotal);
    List<String> notices = new java.util.ArrayList<>();
    notices.add(
        "Incomplete: known ex-tax invoice-line subtotal; export completeness is unverified");
    if (lines.isEmpty())
      notices.add("No attributed line detail; header amount is separate source evidence");
    if (difference != null && difference.abs().compareTo(new BigDecimal("0.02")) > 0)
      notices.add("Unreconciled: known lines differ from the source ex-tax header amount");
    boolean ambiguous = invoices.hasAmbiguousInvoiceNumber(header.invoiceNumber());
    if (ambiguous)
      notices.add("Number-only ingestion flags are unscoped because this invoice number is shared");
    if (!"AVAILABLE".equals(status))
      notices.add(
          "PDF relationship "
              + status.toLowerCase(java.util.Locale.ROOT)
              + "; this does not establish that no source document exists");
    return new InvoiceDetail(
        header.supplierName(),
        header.invoiceNumber(),
        header.invoiceDate(),
        header.dueDate(),
        header.totalAmount(),
        header.purchaseNumber(),
        "AVAILABLE".equals(status),
        header.amountExTax(),
        header.gstAmount(),
        header.freightAmount(),
        lines,
        id,
        header.supplierId(),
        status,
        permissions.isAllowed(role, new ResourceKey("connectors"), PermissionAction.READ),
        ambiguous,
        lineSubtotal,
        difference,
        List.copyOf(notices));
  }

  private InvoiceSummary toSummary(InvoiceMetadataView i) {
    String status = pdfLinks.status(i.invoiceId());
    return new InvoiceSummary(
        i.supplierName(),
        i.invoiceNumber(),
        i.invoiceDate(),
        i.dueDate(),
        i.totalAmount(),
        i.purchaseNumber(),
        "AVAILABLE".equals(status),
        i.invoiceId(),
        i.supplierId(),
        status);
  }

  private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
    return date != null
        && (from == null || !date.isBefore(from))
        && (to == null || !date.isAfter(to));
  }

  /** One current invoice header row for the list endpoint. */
  public record InvoiceSummary(
      String supplierName,
      String invoiceNumber,
      LocalDate invoiceDate,
      LocalDate dueDate,
      BigDecimal totalAmount,
      String purchaseNumber,
      boolean hasPdf,
      java.util.UUID invoiceId,
      java.util.UUID supplierId,
      String pdfStatus) {}

  /** One current invoice header plus its line graph nodes, for the detail endpoint. */
  public record InvoiceDetail(
      String supplierName,
      String invoiceNumber,
      LocalDate invoiceDate,
      LocalDate dueDate,
      BigDecimal totalAmount,
      String purchaseNumber,
      boolean hasPdf,
      BigDecimal amountExTax,
      BigDecimal gstAmount,
      BigDecimal freightAmount,
      List<LineGraphNode> lines,
      java.util.UUID invoiceId,
      java.util.UUID supplierId,
      String pdfStatus,
      boolean canReadDocuments,
      boolean numberAmbiguous,
      BigDecimal lineSubtotal,
      BigDecimal headerDifference,
      List<String> notices) {}
}
