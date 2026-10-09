package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;

/** The invoice node graph's read model: ranked per-level data for supplier → invoice → line drill. */
public interface InvoiceGraphQuery {
  List<SupplierGraphNode> suppliers(LocalDate from, LocalDate to);

  List<InvoiceGraphNode> invoicesForSupplier(String supplier, LocalDate from, LocalDate to);

  List<LineGraphNode> linesForInvoice(String invoiceNumber);
}
