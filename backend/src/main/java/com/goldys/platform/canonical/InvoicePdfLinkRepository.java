package com.goldys.platform.canonical;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface InvoicePdfLinkRepository extends JpaRepository<InvoicePdfLink, UUID> {

  Optional<InvoicePdfLink> findFirstByInvoiceNumber(String invoiceNumber);

  @Query("select l.invoiceNumber from InvoicePdfLink l")
  List<String> findAllInvoiceNumbers();
}
