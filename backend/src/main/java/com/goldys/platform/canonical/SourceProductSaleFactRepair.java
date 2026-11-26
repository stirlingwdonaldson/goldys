package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Maintenance guard: recover an empty CTB day, never replace existing history. */
@Service
public class SourceProductSaleFactRepair {
  private final SourceProductSaleFactRepository repository;
  private final SourceProductSaleFactIngest ingest;
  private final JdbcTemplate jdbc;

  public SourceProductSaleFactRepair(
      SourceProductSaleFactRepository repository,
      SourceProductSaleFactIngest ingest,
      JdbcTemplate jdbc) {
    this.repository = repository;
    this.ingest = ingest;
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(LocalDate day) {
    jdbc.execute("SET LOCAL lock_timeout = '10s'");
    repository.lockReportDay(day);
  }

  @Transactional(readOnly = true)
  public Snapshot snapshot(List<SourceProductSaleFactInput> inputs) {
    var day = validate(inputs);
    var history = repository.recoveryHistory(day);
    var expected =
        inputs.stream()
            .collect(
                Collectors.toMap(SourceProductSaleFactInput::sourceRecordRef, Function.identity()));
    boolean identical =
        history.size() == inputs.size()
            && history.stream()
                .allMatch(
                    row -> {
                      var input = expected.get(row.sourceRecordRef());
                      return input != null
                          && row.supersededAt() == null
                          && row.rawRecordId().equals(input.rawRecordId())
                          && row.sameFact(input);
                    });
    return new Snapshot(
        history.isEmpty() ? State.READY : identical ? State.ALREADY_PRESENT : State.HELD,
        history.stream().map(SourceProductSaleFact::id).toList());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public int restore(List<SourceProductSaleFactInput> inputs) {
    lock(validate(inputs));
    var before = snapshot(inputs);
    if (before.state() == State.ALREADY_PRESENT) return 0;
    if (before.state() != State.READY)
      throw new IllegalStateException("Existing CTB history requires review");
    if (inputs.getFirst().sourceRecordRef().startsWith("ctb-row:"))
      ingest.recordReport(inputs, inputs.getFirst().rawRecordId());
    else ingest.recordAll(inputs);
    repository.flush();
    if (snapshot(inputs).state() != State.ALREADY_PRESENT)
      throw new IllegalStateException("Recovery did not restore every expected fact; rolled back");
    return inputs.size();
  }

  private static LocalDate validate(List<SourceProductSaleFactInput> inputs) {
    if (inputs.isEmpty()) throw new IllegalArgumentException("Recovery requires source facts");
    var first = inputs.getFirst();
    if (inputs.stream()
        .anyMatch(
            i ->
                !"CTB".equals(i.sourceSystem())
                    || !first.tradingDate().equals(i.tradingDate())
                    || !first.rawRecordId().equals(i.rawRecordId())))
      throw new IllegalArgumentException("Recovery requires one retained CTB day");
    return first.tradingDate();
  }

  public enum State {
    READY,
    ALREADY_PRESENT,
    HELD
  }

  public record Snapshot(State state, List<UUID> versions) {}
}
