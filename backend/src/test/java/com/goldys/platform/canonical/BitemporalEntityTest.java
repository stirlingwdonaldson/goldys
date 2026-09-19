package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BitemporalEntityTest {
  @Test
  void supersessionClosesSystemTimeOnly() {
    var entity = TestEntity.recordedAt(Instant.parse("2026-09-19T10:00:00Z"));

    entity.supersede(Instant.parse("2026-09-19T11:00:00Z"));

    assertThat(entity.validFrom()).isEqualTo(TestEntity.VALID_FROM);
    assertThat(entity.recordedAt()).isEqualTo(Instant.parse("2026-09-19T10:00:00Z"));
    assertThat(entity.supersededAt()).isEqualTo(Instant.parse("2026-09-19T11:00:00Z"));
  }

  @Test
  void supersessionCannotMoveBackwardOrRepeat() {
    var entity = TestEntity.recordedAt(Instant.parse("2026-09-19T10:00:00Z"));

    assertThatThrownBy(() -> entity.supersede(Instant.parse("2026-09-19T09:59:59Z")))
        .isInstanceOf(IllegalArgumentException.class);

    entity.supersede(Instant.parse("2026-09-19T11:00:00Z"));

    assertThatThrownBy(() -> entity.supersede(Instant.parse("2026-09-19T12:00:00Z")))
        .isInstanceOf(IllegalStateException.class);
  }

  private static final class TestEntity extends BitemporalEntity {
    static final Instant VALID_FROM = Instant.parse("2026-09-01T00:00:00Z");

    TestEntity(Instant recordedAt) {
      super(UUID.randomUUID(), "CTB", "line-1", UUID.randomUUID(), VALID_FROM, recordedAt);
    }

    static TestEntity recordedAt(Instant recordedAt) {
      return new TestEntity(recordedAt);
    }
  }
}
