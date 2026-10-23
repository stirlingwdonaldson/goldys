package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records source-native product-day measures, closing the prior version on change. */
@Service
class SourceProductSaleFactService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final SourceProductSaleFactRepository repository;

  SourceProductSaleFactService(SourceProductSaleFactRepository repository) {
    this.repository = repository;
  }

  @Transactional
  void record(SourceProductSaleFactInput input) {
    recordAt(input, CLOCK.instant());
  }

  /** Batch record for replay: one transaction, each input deduped/superseded on its source ref. */
  @Transactional
  void recordAll(List<SourceProductSaleFactInput> inputs) {
    for (SourceProductSaleFactInput input : inputs) {
      recordAt(input, CLOCK.instant());
    }
  }

  @Transactional
  void recordReport(List<SourceProductSaleFactInput> inputs, UUID completedRawId) {
    if (inputs.isEmpty()) return;
    var day = inputs.getFirst().tradingDate();
    if (inputs.stream()
        .anyMatch(
            i ->
                !i.sourceSystem().equals("CTB")
                    || !i.tradingDate().equals(day)
                    || !i.sourceRecordRef().startsWith("ctb-row:")))
      throw new IllegalArgumentException(
          "Expected one complete dated CTB report with row identities");
    repository.lockReportDay(day);
    Map<String, List<SourceProductSaleFactInput>> products =
        inputs.stream()
            .collect(
                Collectors.groupingBy(
                    i -> ProductSalesInput.sourceIdentity(i.stockCode(), i.siteNumber()),
                    LinkedHashMap::new,
                    Collectors.toList()));
    Instant now = CLOCK.instant();
    for (var product : products.entrySet()) {
      var first = product.getValue().getFirst();
      var current =
          repository.lockCurrentProduct("CTB", day, first.stockCode(), first.siteNumber());
      String reportRef = "ctb-report:" + product.getKey() + "@" + day;
      if (repository.hasLaterProductObservation(
              "CTB", day, first.stockCode(), first.siteNumber(), completedRawId)
          || repository.hasLaterObservation("CTB", reportRef, completedRawId)
          || repository.registerObservation("CTB", reportRef, completedRawId) == 0) continue;
      var refs =
          product.getValue().stream()
              .map(SourceProductSaleFactInput::sourceRecordRef)
              .collect(Collectors.toSet());
      // Row numbers can move between reports; close the old representation, never add both.
      for (var old : current) if (!refs.contains(old.sourceRecordRef())) old.supersede(now);
      repository.flush();
      for (var input : product.getValue()) recordAt(input, now);
    }
  }

  @Transactional
  void recordAt(SourceProductSaleFactInput input, Instant recordedAt) {
    String sourceRef = input.sourceRecordRef();
    if (input.sourceSystem().equals("CTB") && !sourceRef.startsWith("ctb-row:"))
      repository.lockReportDay(input.tradingDate());
    // Legacy page replay cannot reintroduce the representation replaced by a complete report.
    if (input.sourceSystem().equals("CTB")
        && !sourceRef.startsWith("ctb-row:")
        && repository
            .lockCurrentProduct("CTB", input.tradingDate(), input.stockCode(), input.siteNumber())
            .stream()
            .anyMatch(f -> f.sourceRecordRef().startsWith("ctb-row:"))) return;
    UUID logicalId =
        UUID.nameUUIDFromBytes(
            ("source-product-sale-fact:" + input.sourceSystem() + ":" + sourceRef)
                .getBytes(StandardCharsets.UTF_8));
    Optional<SourceProductSaleFact> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), sourceRef);

    if (current.isPresent()
        && current.get().siteNumber() != null
        && input.siteNumber() != null
        && !current.get().siteNumber().equals(input.siteNumber()))
      throw new IllegalArgumentException(
          "Site-scoped source observation collision requires review");
    // A receipt is registered even when its values equal the current fact. Otherwise replaying
    // an identical intermediate receipt after a correction would manufacture an old revision.
    if (repository.registerObservation(input.sourceSystem(), sourceRef, input.rawRecordId()) == 0
        || repository.hasLaterObservation(input.sourceSystem(), sourceRef, input.rawRecordId()))
      return;

    if (current.isPresent()) {
      SourceProductSaleFact existing = current.get();
      if (existing.sameFact(input)) {
        return;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    SourceProductSaleFact row =
        SourceProductSaleFact.create(
            logicalId,
            input.sourceSystem(),
            sourceRef,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.tradingDate(),
            input.stockCode(),
            input.costPerUnit(),
            input.foodCost(),
            input.totalFoodCost(),
            input.profit(),
            input.discountedAmount(),
            input.taxAmount(),
            input.unitPrice(),
            input.uniqueGroupId());
    row.captureSourceFields(input);
    repository.save(row);
  }
}
