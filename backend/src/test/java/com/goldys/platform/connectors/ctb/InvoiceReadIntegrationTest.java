package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.application.*;
import com.goldys.platform.auth.*;
import com.goldys.platform.canonical.*;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class InvoiceReadIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired CtInvoiceCsvIngestService ingest;
  @Autowired InvoiceReportingService reporting;
  @Autowired InvoiceGraphService graph;
  @Autowired InventoryReportingService inventory;
  @Autowired InvoicePdfLinkService links;
  @Autowired InvoiceDocumentService documents;
  final UserRole owner = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  final LocalDate day = LocalDate.of(2026, 10, 1);

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate invoice_pdf_link, stock_item, canonical_invoice_line, canonical_invoice, canonical_supplier, invoice_ingest_flag");
  }

  void importInvoices() {
    ingest.ingest(
        ("Invoice,Supplier,SupplierCode,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "SHARED,A,SUP-A,2026-10-01,A,Item A,1,100\n"
                + "SHARED,B,SUP-B,2026-10-01,B,Item B,1,200\n"
                + "UNIQUE,A,SUP-A,2026-10-01,C,Item C,1,30\n")
            .getBytes(StandardCharsets.UTF_8));
  }

  List<InvoiceReportingService.InvoiceSummary> headers() {
    return reporting.list(owner, null, day, day, 0, 50).items();
  }

  @Test
  void storedParentIdentitySurvivesListDetailGraphAndSupplierBreakdown() {
    importInvoices();
    var headers = headers();
    assertThat(headers).hasSize(3);
    assertThat(headers)
        .extracting(InvoiceReportingService.InvoiceSummary::invoiceId)
        .doesNotHaveDuplicates();
    for (var header : headers) {
      var detail = reporting.detailById(owner, header.invoiceId());
      assertThat(detail.lines()).hasSize(1);
      assertThat(detail.supplierId()).isEqualTo(header.supplierId());
      assertThat(graph.linesForInvoiceId(owner, header.invoiceId())).hasSize(1);
      assertThat(detail.lineSubtotal())
          .isEqualByComparingTo(
              header.supplierName().equals("B")
                  ? "200"
                  : header.invoiceNumber().equals("UNIQUE") ? "30" : "100");
    }
    assertThatThrownBy(() -> reporting.detail(owner, "SHARED"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(reporting.detail(owner, "UNIQUE").lines()).hasSize(1);
    var suppliers = graph.suppliers(owner, day, day);
    assertThat(suppliers).hasSize(2);
    assertThat(suppliers.stream().map(s -> s.totalSpend()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("330");
    assertThat(inventory.summary(owner, day, day).purchases()).isEqualByComparingTo("330");
    assertThat(inventory.lineBreakdown(owner, day, day).cogsBySupplier())
        .allSatisfy(
            s -> {
              assertThat(s.wetAmount()).isNull();
              assertThat(s.notices()).anyMatch(n -> n.contains("WET incomplete"));
            });
    assertThat(graph.invoicesForSupplierId(owner, headers.getFirst().supplierId(), day, day))
        .allSatisfy(n -> assertThat(n.invoiceId()).isNotNull());
  }

  @Test
  void legacyPdfAvailabilityCanBeReadWithoutAWriteTransaction() {
    // HTTP reads have no enclosing write transaction; a PDF filename must not require one.
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    ingest.ingest(
        ("Invoice,Supplier,SupplierCode,Date,PDF,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "PDF-1,A,SUP-A,2026-10-01,invoice.pdf,A,Item A,1,100\n")
            .getBytes(StandardCharsets.UTF_8));
    UUID raw = jdbc.queryForObject("select raw_record_id from canonical_invoice", UUID.class);
    jdbc.update(
        "insert into invoice_pdf_link(pdf_raw_record_id,invoice_number,pdf_filename,link_source,linked_at) values (?, 'PDF-1','invoice.pdf','BACKFILL',now())",
        raw);

    var header = headers().getFirst();
    assertThat(header.pdfStatus()).isEqualTo("AVAILABLE");
    assertThat(reporting.detailById(owner, header.invoiceId()).hasPdf()).isTrue();
    assertThat(documents.documents(owner, header.invoiceId()))
        .singleElement()
        .satisfies(document -> assertThat(document.filename()).isEqualTo("invoice.pdf"));

    // A filename shared by different suppliers does not qualify a legacy number-only PDF link.
    ingest.ingest(
        ("Invoice,Supplier,SupplierCode,Date,PDF,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "PDF-2,B,SUP-B,2026-10-01,invoice.pdf,B,Item B,1,200\n")
            .getBytes(StandardCharsets.UTF_8));
    var ambiguous =
        headers().stream()
            .filter(h -> h.invoiceId().equals(header.invoiceId()))
            .findFirst()
            .orElseThrow();
    assertThat(ambiguous.pdfStatus()).isEqualTo("UNRESOLVED");
    assertThat(documents.documents(owner, header.invoiceId())).isEmpty();
  }

  @Test
  void outOfRangeAmbiguousLegacyPdfDoesNotBlockListOrUniqueDetail() {
    importInvoices();
    UUID raw =
        jdbc.queryForObject("select raw_record_id from canonical_invoice limit 1", UUID.class);
    jdbc.update(
        "insert into invoice_pdf_link(pdf_raw_record_id,invoice_number,link_source,linked_at) values (?, 'SHARED','BACKFILL',now())",
        raw);
    assertThat(headers()).hasSize(3);
    assertThat(reporting.detail(owner, "UNIQUE").invoiceNumber()).isEqualTo("UNIQUE");
    assertThat(graph.suppliers(owner, day.plusDays(1), day.plusDays(2))).isEmpty();
    for (var header : headers())
      if (header.invoiceNumber().equals("SHARED")) {
        assertThat(reporting.detailById(owner, header.invoiceId()).pdfStatus())
            .isEqualTo("UNRESOLVED");
        assertThat(documents.documents(owner, header.invoiceId())).isEmpty();
      }
  }

  @Test
  void qualifiedDocumentsRejectCrossInvoiceAccessAndKeepFilenameWithBytes() {
    importInvoices();
    var a =
        headers().stream()
            .filter(h -> h.invoiceNumber().equals("UNIQUE"))
            .findFirst()
            .orElseThrow();
    var b = headers().stream().filter(h -> h.supplierName().equals("B")).findFirst().orElseThrow();
    UUID raw =
        jdbc.queryForObject(
            "select raw_record_id from canonical_invoice where logical_entity_id=?",
            UUID.class,
            a.invoiceId());
    jdbc.update(
        "insert into invoice_pdf_link(pdf_raw_record_id,invoice_number,pdf_filename,link_source,linked_at,invoice_logical_id) values (?, 'UNIQUE','selected.pdf','ENRICHMENT',now(),?)",
        raw,
        a.invoiceId());
    assertThat(documents.pdf(owner, a.invoiceId(), raw).filename()).isEqualTo("selected.pdf");
    assertThat(documents.pdf(owner, a.invoiceId(), raw).bytes()).isNotEmpty();
    assertThatThrownBy(() -> documents.pdf(owner, b.invoiceId(), raw))
        .isInstanceOf(NoSuchElementException.class);
    links.link("UNIQUE", raw, "different.pdf", InvoicePdfLinkSource.ENRICHMENT);
    assertThat(documents.documents(owner, a.invoiceId())).hasSize(1);
    assertThat(documents.pdf(owner, a.invoiceId(), raw).filename()).isEqualTo("selected.pdf");
  }

  @Test
  void customCostGrantDoesNotImplyRawSourceGrantAndRevocationIsEnforced() {
    importInvoices();
    UserRole viewer = new UserRole(new DepartmentCode("INVOICE_TEST"), new SeniorityCode("VIEWER"));
    jdbc.update(
        "insert into permission(id,department,seniority,resource,can_read,can_write) values (gen_random_uuid(),'INVOICE_TEST','VIEWER','inventory.cost',true,false) on conflict (department,seniority,resource) do update set can_read=true");
    var id = headers().getFirst().invoiceId();
    assertThat(reporting.detailById(viewer, id).canReadDocuments()).isFalse();
    assertThatThrownBy(() -> documents.documents(viewer, id))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update(
        "insert into permission(id,department,seniority,resource,can_read,can_write) values (gen_random_uuid(),'INVOICE_TEST','VIEWER','connectors',true,false) on conflict (department,seniority,resource) do update set can_read=true");
    assertThat(reporting.detailById(viewer, id).canReadDocuments()).isTrue();
    assertThat(documents.documents(viewer, id)).isEmpty();
    jdbc.update(
        "update permission set can_read=false where department='INVOICE_TEST' and seniority='VIEWER' and resource='connectors'");
    assertThatThrownBy(() -> documents.documents(viewer, id))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void migrationFromV94KeepsUnqualifiedEvidenceAndValidates() {
    Flyway.configure()
        .dataSource(jdbc.getDataSource())
        .schemas("invoice_read_upgrade")
        .defaultSchema("invoice_read_upgrade")
        .target("94")
        .load()
        .migrate();
    UUID run = UUID.randomUUID(), raw = UUID.randomUUID();
    jdbc.update(
        "insert into invoice_read_upgrade.ingestion_run(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) values (?,'CTB','fixture','SUCCESS',now(),1,1)",
        run);
    jdbc.update(
        "insert into invoice_read_upgrade.raw_record(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,payload_sha256,payload_byte_length,fetcher_identity,fetched_at) values (?,?,'CTB','FILE_EXPORT','application/pdf',? ,?,1,'fixture',now())",
        raw,
        run,
        new byte[] {1},
        "0".repeat(64));
    jdbc.update(
        "insert into invoice_read_upgrade.invoice_pdf_link(pdf_raw_record_id,invoice_number,pdf_filename,link_source,linked_at) values (?,'OLD','old.pdf','BACKFILL',now())",
        raw);
    var upgrade =
        Flyway.configure()
            .dataSource(jdbc.getDataSource())
            .schemas("invoice_read_upgrade")
            .defaultSchema("invoice_read_upgrade")
            .load();
    upgrade.migrate();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from invoice_read_upgrade.invoice_pdf_link where pdf_raw_record_id=? and invoice_logical_id is null and pdf_filename='old.pdf'",
                Integer.class,
                raw))
        .isEqualTo(1);
    assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select is_nullable from information_schema.columns where table_schema='invoice_read_upgrade' and table_name='invoice_pdf_link' and column_name='invoice_logical_id'",
                String.class))
        .isEqualTo("YES");
  }
}
