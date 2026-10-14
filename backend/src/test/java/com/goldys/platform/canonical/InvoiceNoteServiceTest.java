package com.goldys.platform.canonical;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InvoiceNoteServiceTest {

  private final InvoiceNoteRepository repository = mock(InvoiceNoteRepository.class);
  private final InvoiceNoteService service = new InvoiceNoteService(repository);

  @Test
  void recordsANewNote() {
    when(repository.findById("INV-1")).thenReturn(Optional.empty());

    service.record("INV-1", "(2 BOXES)");

    verify(repository).save(any(InvoiceNote.class));
  }

  @Test
  void overwritesADifferentNote() {
    InvoiceNote existing = InvoiceNote.create("INV-1", "COMMENT", Instant.EPOCH);
    when(repository.findById("INV-1")).thenReturn(Optional.of(existing));

    service.record("INV-1", "(3 BOXES)");

    verify(repository).save(any(InvoiceNote.class));
  }

  @Test
  void isANoOpWhenTheSameNoteAlreadyExists() {
    InvoiceNote existing = InvoiceNote.create("INV-1", "(2 BOXES)", Instant.EPOCH);
    when(repository.findById("INV-1")).thenReturn(Optional.of(existing));

    service.record("INV-1", "(2 BOXES)");

    verify(repository, never()).save(any(InvoiceNote.class));
  }

  @Test
  void ignoresABlankNote() {
    service.record("INV-1", "   ");

    verify(repository, never()).save(any(InvoiceNote.class));
  }

  @Test
  void ignoresANullInvoiceNumber() {
    service.record(null, "note");

    verify(repository, never()).save(any(InvoiceNote.class));
  }
}
