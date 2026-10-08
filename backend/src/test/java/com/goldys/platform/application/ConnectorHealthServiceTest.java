package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.ingestion.IngestionRunSummary;
import com.goldys.platform.ingestion.IngestionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorHealthServiceTest {

  @Test
  void mapsLatestRunsToHealth() {
    IngestionService ingestion = mock(IngestionService.class);
    when(ingestion.latestRunPerSource())
        .thenReturn(
            List.of(
                new IngestionRunSummary(
                    "OPENTABLE",
                    "opentable-csv-drop",
                    "SUCCESS",
                    Instant.parse("2026-10-06T10:00:00Z"),
                    "0",
                    null)));
    ConnectorHealthService service = new ConnectorHealthService(ingestion);

    var health = service.health();

    assertThat(health).hasSize(1);
    assertThat(health.get(0).source()).isEqualTo("OPENTABLE");
    assertThat(health.get(0).status()).isEqualTo("SUCCESS");
    assertThat(health.get(0).lastRunAt()).isEqualTo(Instant.parse("2026-10-06T10:00:00Z"));
  }
}
