package com.goldys.platform.canonical;

import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link InvoiceNote}, keyed by invoice number. */
interface InvoiceNoteRepository extends JpaRepository<InvoiceNote, String> {}
