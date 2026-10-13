package com.goldys.platform.canonical;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the concatenated delivery-note annotations parsed from an invoice's PDF. Idempotent:
 * writing the same note again is a no-op, so a replay never churns the row. Plain evidence, not a
 * fact — recording a note never supersedes the invoice.
 */
@Service
public class InvoiceNoteService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final InvoiceNoteRepository repository;

  public InvoiceNoteService(InvoiceNoteRepository repository) {
    this.repository = repository;
  }

  /** Records {@code note} for {@code invoiceNumber}, overwriting an older, different note. */
  @Transactional
  public void record(String invoiceNumber, String note) {
    if (invoiceNumber == null || note == null || note.isBlank()) {
      return;
    }
    InvoiceNote existing = repository.findById(invoiceNumber).orElse(null);
    if (existing != null && existing.note().equals(note)) {
      return;
    }
    repository.save(InvoiceNote.create(invoiceNumber, note, CLOCK.instant()));
  }
}
