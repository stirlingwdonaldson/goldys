package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Maintenance facade: snapshot facts and make a guarded, quantity-only versioned correction. */
@Service
public class CanonicalSaleItemRepair {
  private final CanonicalSaleItemRepository repository;
  private final JdbcTemplate jdbc;
  private final ApplicationEventPublisher publisher;

  public CanonicalSaleItemRepair(
      CanonicalSaleItemRepository repository,
      JdbcTemplate jdbc,
      ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.publisher = publisher;
  }

  @Transactional(readOnly = true)
  public List<Snapshot> snapshot(String source, LocalDate day, Collection<String> refs) {
    return repository.repairSnapshot(source, day, refs).stream()
        .map(
            s ->
                new Snapshot(
                    s.id(), s.repairInput(), s.validFrom(), s.validTo(), s.saleClosedTime()))
        .toList();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockForRepair() {
    // Include absent keys: row locks alone cannot prevent concurrent inserts after preview.
    jdbc.execute("SET LOCAL lock_timeout = '10s'");
    jdbc.execute("LOCK TABLE canonical_sale_item IN SHARE ROW EXCLUSIVE MODE");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void correctQuantity(
      String source,
      String ref,
      UUID expectedVersion,
      BigDecimal expectedQuantity,
      BigDecimal quantity,
      UUID rawId) {
    CanonicalSaleItem previous = repository.lockCurrentSourceFact(source, ref).orElseThrow();
    if (!previous.id().equals(expectedVersion)
        || previous.quantitySold().compareTo(expectedQuantity) != 0)
      throw new IllegalStateException("Quantity correction no longer matches the reviewed version");
    Instant now = Instant.now();
    CanonicalSaleItem successor = new CanonicalSaleItem(previous, quantity, rawId, now);
    previous.supersede(now);
    repository.saveAndFlush(previous);
    repository.save(successor);
    publisher.publishEvent(new SaleItemRecorded(previous.tradingDate()));
  }

  public record Snapshot(
      UUID versionId,
      SaleItemInput fact,
      Instant validFrom,
      Instant validTo,
      java.time.LocalTime saleClosedTime) {}
}
