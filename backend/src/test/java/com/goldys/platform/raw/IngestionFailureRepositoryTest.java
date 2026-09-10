package com.goldys.platform.raw;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Ingestion failures must be queryable independently of successful ingestion (spec Requirement 6,
 * Build Order Prerequisite #4). The point of the separate table is that a failed connector run
 * looks different from a connector run that simply had nothing new - if that distinction is not
 * queryable, an outage surfaces later as a phantom reconciliation mismatch.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class IngestionFailureRepositoryTest {

  private static final Instant FIRST = Instant.parse("2026-07-25T06:00:00Z");

  @Autowired private IngestionFailureRepository ingestionFailureRepository;

  @Test
  void returnsOneSourcesFailuresNewestFirst() {
    ingestionFailureRepository.saveAndFlush(
        new IngestionFailure(
            SourceSystem.DEPUTY, "TIMEOUT", "read timed out", FIRST.plusSeconds(60)));
    ingestionFailureRepository.saveAndFlush(
        new IngestionFailure(SourceSystem.LIGHTSPEED, "AUTH_FAILURE", "session expired", FIRST));
    ingestionFailureRepository.saveAndFlush(
        new IngestionFailure(
            SourceSystem.LIGHTSPEED,
            "SCHEMA_MISMATCH",
            "unexpected column",
            FIRST.plusSeconds(120)));

    List<IngestionFailure> failures =
        ingestionFailureRepository.findBySourceSystemOrderByOccurredAtDesc(SourceSystem.LIGHTSPEED);

    assertThat(failures)
        .extracting(IngestionFailure::getSourceSystem)
        .containsOnly(SourceSystem.LIGHTSPEED);
    assertThat(failures)
        .extracting(IngestionFailure::getFailureType)
        .containsExactly("SCHEMA_MISMATCH", "AUTH_FAILURE");
    assertThat(failures)
        .extracting(IngestionFailure::getOccurredAt)
        .isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }

  @Test
  void failureTypeIsOpenEndedSoNewConnectorsNeedNoSchemaChange() {
    // Deliberately a String, not an enum: a connector reporting an unanticipated failure mode must
    // be able to record it without a migration first.
    IngestionFailure saved =
        ingestionFailureRepository.saveAndFlush(
            new IngestionFailure(
                SourceSystem.OPENTABLE, "GUESTCENTER_UI_CHANGED", "selector not found", FIRST));

    assertThat(ingestionFailureRepository.findById(saved.getId()))
        .get()
        .extracting(IngestionFailure::getFailureType)
        .isEqualTo("GUESTCENTER_UI_CHANGED");
  }

  @Test
  void detailIsOptionalButTheFailureIsStillRecorded() {
    // A failure with no useful message is still a failure state - it must not be dropped.
    IngestionFailure saved =
        ingestionFailureRepository.saveAndFlush(
            new IngestionFailure(SourceSystem.COOKING_THE_BOOKS, "PARTIAL_FETCH", null, FIRST));

    assertThat(ingestionFailureRepository.findById(saved.getId())).isPresent();
  }
}
