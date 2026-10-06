package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.connectors.opentable.OpenTableCsvIngestService;
import com.goldys.platform.ingestion.FailureDetail;
import com.goldys.platform.ingestion.IngestionRunSummary;
import com.goldys.platform.ingestion.IngestionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorApplicationServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private final PermissionService permissions = mock(PermissionService.class);
  private final IngestionService ingestion = mock(IngestionService.class);
  private final OpenTableCsvIngestService openTable = mock(OpenTableCsvIngestService.class);
  private final ConnectorApplicationService service =
      new ConnectorApplicationService(ingestion, openTable, permissions);

  @Test
  void mergesKnownSourcesWithLatestRunAndFlagsRunnable() {
    when(ingestion.latestRunPerSource())
        .thenReturn(
            List.of(
                new IngestionRunSummary(
                    "CTB", "ctb-revenue", "SUCCESS", Instant.EPOCH, null, null)));
    when(ingestion.isRunnable("CTB")).thenReturn(true);
    when(ingestion.isRunnable("LIGHTSPEED")).thenReturn(false);

    var statuses = service.connectors(OWNER);

    assertThat(statuses).hasSize(4);
    var ctb = statuses.get(1);
    assertThat(ctb.source()).isEqualTo("CTB");
    assertThat(ctb.status()).isEqualTo("success");
    assertThat(ctb.runnable()).isTrue();
    assertThat(statuses.get(2).status()).isEqualTo("never_run");
  }

  @Test
  void mapsFailureDetailAndFailureCount() {
    when(ingestion.latestRunPerSource())
        .thenReturn(
            List.of(
                new IngestionRunSummary(
                    "CTB",
                    "ctb-revenue",
                    "FAILED",
                    Instant.parse("2026-10-02T12:00:00Z"),
                    "1 failure(s) recorded",
                    new FailureDetail(
                        "AUTH_FAILED",
                        "OAuth rejected",
                        Instant.parse("2026-10-02T12:00:05Z"),
                        null))));
    when(ingestion.isRunnable("CTB")).thenReturn(true);

    var statuses = service.connectors(OWNER);

    var ctb = statuses.get(1);
    assertThat(ctb.status()).isEqualTo("failed");
    assertThat(ctb.failureCount()).isEqualTo(1);
    assertThat(ctb.failure().type()).isEqualTo("AUTH_FAILED");
  }

  @Test
  void readDeniedThrows() {
    doThrow(AccessDeniedException.forResource("connectors"))
        .when(permissions)
        .require(any(), any(), any());

    assertThatThrownBy(() -> service.connectors(OWNER)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void runAuthorizesThenDelegates() {
    when(ingestion.runConnector("CTB"))
        .thenReturn(
            new IngestionRunSummary("CTB", "ctb-revenue", "SUCCESS", Instant.EPOCH, null, null));
    when(ingestion.isRunnable("CTB")).thenReturn(true);

    var status = service.run(OWNER, "CTB");

    assertThat(status.status()).isEqualTo("success");
    verify(ingestion).runConnector("CTB");
  }

  @Test
  void uploadAuthorizesThenDelegates() {
    byte[] bytes = {1, 2, 3};

    service.uploadOpenTableCsv(OWNER, bytes);

    verify(openTable).ingest(bytes);
  }
}
