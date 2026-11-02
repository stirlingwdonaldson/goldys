package com.goldys.platform.canonical;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public atomic write boundary; no source-specific staging or mutation of financial facts. */
@Service
public class CanonicalReceiptEvidenceIngest {
  private final EntityManager entities;
  private final org.springframework.context.ApplicationEventPublisher publisher;

  public CanonicalReceiptEvidenceIngest(
      EntityManager entities, org.springframework.context.ApplicationEventPublisher publisher) {
    this.entities = entities;
    this.publisher = publisher;
  }

  @Transactional
  public void record(
      String kind,
      UUID rawId,
      UUID scopeRawId,
      ReceiptImportScope scope,
      List<ReceiptMappingInput> mappings,
      List<SaleTransactionInput> transactions) {
    entities.persist(
        new CanonicalReceiptEvidenceBatch(kind, rawId, scopeRawId, scope, mappings, transactions));
    entities.flush();
    publisher.publishEvent(new ReceiptEvidenceRecorded(rawId));
  }
}
