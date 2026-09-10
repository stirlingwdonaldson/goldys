package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Pins the two-axis versioning pattern (spec Requirement 2) that every canonical entity inherits.
 *
 * <p>These are the guarantees the rest of the system is built on: "query as of a past system time"
 * and "recompute a resolution rule without losing history" are only meaningful while valid time and
 * system time stay independent, and while superseding closes a row instead of overwriting it.
 */
class BitemporalEntityTest {

  private static final Instant VALID_FROM = Instant.parse("2026-07-25T17:00:00Z");
  private static final Instant RECORDED_AT = Instant.parse("2026-07-26T09:00:00Z");

  /**
   * Minimal concrete subclass - the base type is abstract and has no entity semantics of its own.
   */
  private static final class TestEntity extends BitemporalEntity {
    private TestEntity(Instant validFrom, Instant recordedAt) {
      super(validFrom, recordedAt);
    }
  }

  @Test
  void newRowIsCurrentAndOpenEndedOnBothAxes() {
    TestEntity row = new TestEntity(VALID_FROM, RECORDED_AT);

    assertThat(row.isCurrent()).isTrue();
    assertThat(row.getSupersededAt()).isNull();
    assertThat(row.getValidTo()).isNull();
    assertThat(row.getValidFrom()).isEqualTo(VALID_FROM);
    assertThat(row.getRecordedAt()).isEqualTo(RECORDED_AT);
  }

  @Test
  void supersedingClosesSystemTimeOnly() {
    TestEntity row = new TestEntity(VALID_FROM, RECORDED_AT);
    Instant supersededAt = Instant.parse("2026-07-27T11:00:00Z");

    row.supersede(supersededAt);

    assertThat(row.isCurrent()).isFalse();
    assertThat(row.getSupersededAt()).isEqualTo(supersededAt);

    // Learning something new about the past does not change when the fact was true. If supersede()
    // also moved valid_to, historical "as of" queries would report a fact as having ended at the
    // moment we corrected it.
    assertThat(row.getValidTo()).isNull();
    assertThat(row.getValidFrom()).isEqualTo(VALID_FROM);
    assertThat(row.getRecordedAt()).isEqualTo(RECORDED_AT);
  }

  @Test
  void validTimeAndSystemTimeAreIndependent() {
    TestEntity row = new TestEntity(VALID_FROM, RECORDED_AT);
    Instant validTo = Instant.parse("2026-07-25T23:00:00Z");

    row.setValidTo(validTo);

    // Closing a fact's valid time is not the same operation as superseding the row that recorded
    // it, and must not close system time. Collapsing the two axes into one timestamp pair is the
    // specific regression this pattern exists to prevent.
    assertThat(row.getValidTo()).isEqualTo(validTo);
    assertThat(row.isCurrent()).isTrue();
    assertThat(row.getSupersededAt()).isNull();
    assertThat(row.getRecordedAt()).isEqualTo(RECORDED_AT);
  }
}
