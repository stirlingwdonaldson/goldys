package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records canonical per-product daily totals; idempotent, with source identity where supplied. */
@Service
class CanonicalProductSalesService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalProductSalesRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalProductSalesService(
      CanonicalProductSalesRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.publisher = publisher;
  }

  @Transactional
  CanonicalProductSales record(ProductSalesInput input) {
    java.util.Set<String> oldNames = namesForDate(input.tradingDate());
    CanonicalProductSales saved = recordAt(input, CLOCK.instant());
    oldNames.add(input.productNameKey());
    for (String name : oldNames)
      publisher.publishEvent(new ProductSalesRecorded(name, input.tradingDate()));
    return saved;
  }

  @Transactional
  void recordAll(java.util.List<ProductSalesInput> inputs) {
    java.util.Map<java.time.LocalDate, java.util.Set<String>> changed =
        new java.util.LinkedHashMap<>();
    for (ProductSalesInput input : inputs) {
      changed.computeIfAbsent(input.tradingDate(), this::namesForDate).add(input.productNameKey());
      recordAt(input, CLOCK.instant());
    }
    changed.forEach(
        (date, names) ->
            names.forEach(name -> publisher.publishEvent(new ProductSalesRecorded(name, date))));
  }

  private java.util.Set<String> namesForDate(java.time.LocalDate date) {
    return repository.findCurrentByDate(date).stream()
        .map(CanonicalProductSales::productNameKey)
        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  @Transactional
  CanonicalProductSales recordAt(ProductSalesInput input, Instant recordedAt) {
    boolean qualified = input.sourceProductRef() != null;
    String ref =
        qualified
            ? "product:" + input.sourceProductRef() + "@" + input.tradingDate()
            : input.productNameKey() + "@" + input.tradingDate();
    UUID logicalId =
        qualified
            ? UUID.nameUUIDFromBytes(
                ("product-sales:" + input.sourceSystem() + ":" + ref)
                    .getBytes(StandardCharsets.UTF_8))
            : logicalIdFor(input.productNameKey(), input.tradingDate());
    Optional<CanonicalProductSales> current =
        qualified
            ? repository.lockQualified(input.sourceSystem(), ref)
            : repository.lockCurrent(
                input.productNameKey(), input.tradingDate(), input.sourceSystem());
    if (qualified
        && current.isPresent()
        && (input.contributorRawIds().stream()
                .allMatch(id -> repository.observed(input.sourceSystem(), ref, id))
            || input.contributorRawIds().stream()
                .allMatch(id -> repository.hasLaterObservation(input.sourceSystem(), ref, id))))
      return current.get();
    if (current.isPresent() && current.get().sameFact(input)) {
      if (qualified)
        for (UUID raw : input.contributorRawIds())
          repository.addContributor(current.get().id(), raw);
      return current.get();
    }
    if (current.isPresent()) {
      current.get().supersede(recordedAt);
      repository.saveAndFlush(current.get());
    }
    CanonicalProductSales row =
        qualified
            ? CanonicalProductSales.qualified(logicalId, input, ref, recordedAt)
            : CanonicalProductSales.create(
                logicalId,
                input.sourceSystem(),
                ref,
                input.rawRecordId(),
                recordedAt,
                recordedAt,
                input.tradingDate(),
                input.productNameKey(),
                input.quantitySold(),
                input.amount(),
                input.stockCode());
    row = repository.saveAndFlush(row);
    if (qualified)
      for (UUID raw : input.contributorRawIds()) repository.addContributor(row.id(), raw);
    return row;
  }

  private static UUID logicalIdFor(String key, java.time.LocalDate date) {
    return UUID.nameUUIDFromBytes(
        ("product-sales:" + key + ":" + date).getBytes(StandardCharsets.UTF_8));
  }
}
