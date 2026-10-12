package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebouncedProjectionFlusherTest {

  @Test
  void aFailingRecomputeRequeuesItsDateWithoutDroppingOthers() {
    DebouncedProjectionFlusher flusher = new DebouncedProjectionFlusher();
    LocalDate a = LocalDate.of(2026, 9, 13);
    LocalDate b = LocalDate.of(2026, 9, 14);
    LocalDate c = LocalDate.of(2026, 9, 15);
    flusher.enqueue(a);
    flusher.enqueue(b);
    flusher.enqueue(c);

    List<LocalDate> recomputed = new ArrayList<>();
    flusher.flush(
        date -> {
          if (date.equals(b)) {
            throw new IllegalStateException("boom");
          }
          recomputed.add(date);
        });

    // The throw on b must not drop a or c.
    assertThat(recomputed).containsExactlyInAnyOrder(a, c);

    // b was re-queued and succeeds on the next flush.
    flusher.flush(recomputed::add);
    assertThat(recomputed).containsExactlyInAnyOrder(a, b, c);
  }

  @Test
  void emptyFlushIsANoOp() {
    DebouncedProjectionFlusher flusher = new DebouncedProjectionFlusher();
    List<LocalDate> recomputed = new ArrayList<>();

    flusher.flush(recomputed::add);

    assertThat(recomputed).isEmpty();
  }
}
