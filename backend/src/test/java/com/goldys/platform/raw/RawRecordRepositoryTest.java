package com.goldys.platform.raw;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Persistence-level checks for the append-only raw log (spec Requirement 1).
 *
 * <p>FlywayAutoConfiguration is imported explicitly: @DataJpaTest does not run migrations on its
 * own, and without them these tests would only pass on a database that happened to be migrated
 * already - they would go green on this machine and red on a clean one.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class RawRecordRepositoryTest {

  private static final Instant FETCHED_AT = Instant.parse("2026-07-26T09:15:00Z");

  @Autowired private RawRecordRepository rawRecordRepository;

  @Test
  void manualEntryCarriesTheWholeEnvelope() {
    // Requirement 1: a staff data entry lands in the same log as any connector payload, tagged
    // MANUAL and attributable to the person who entered it.
    RawRecord manual =
        new RawRecord(
            SourceSystem.MANUAL_ENTRY,
            FetchMethod.MANUAL,
            "application/json",
            "{\"shift\":\"2026-07-25\",\"hours\":7.5}",
            "stirling@donaldsonblack.com.au",
            FETCHED_AT);

    RawRecord saved = rawRecordRepository.saveAndFlush(manual);
    RawRecord reloaded = rawRecordRepository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getSourceSystem()).isEqualTo(SourceSystem.MANUAL_ENTRY);
    assertThat(reloaded.getFetchMethod()).isEqualTo(FetchMethod.MANUAL);
    assertThat(reloaded.getContentType()).isEqualTo("application/json");
    assertThat(reloaded.getFetcherIdentity()).isEqualTo("stirling@donaldsonblack.com.au");
    assertThat(reloaded.getFetchedAt()).isEqualTo(FETCHED_AT);
    assertThat(reloaded.getPayload()).contains("\"hours\":7.5");
  }

  @Test
  void correctionIsANewRowRatherThanAnEdit() {
    // The raw log's core invariant: re-ingesting the same content appends. Nothing in the model
    // deduplicates or overwrites, so a corrected export must be distinguishable from the original
    // it corrects - the canonical layer is what decides which version wins, not this table.
    RawRecord original = csvRecord("Hours,7.5\n", FETCHED_AT);
    RawRecord correction = csvRecord("Hours,7.5\n", FETCHED_AT.plusSeconds(3600));

    RawRecord first = rawRecordRepository.saveAndFlush(original);
    RawRecord second = rawRecordRepository.saveAndFlush(correction);

    assertThat(first.getId()).isNotEqualTo(second.getId());
    assertThat(rawRecordRepository.findById(first.getId())).isPresent();
    assertThat(rawRecordRepository.findById(second.getId())).isPresent();
  }

  @Test
  void wrappedNonJsonPayloadSurvivesIntact() {
    // Scraped HTML and CSV are not JSON. They ride through the same envelope wrapped as
    // {"raw": "..."} rather than getting a source-specific column (connector isolation). For these
    // shapes the wrapper is what makes the payload byte-faithful: it is carried as an opaque string
    // value, so nothing re-parses or reorders it.
    String scrapedHtml =
        "<html><body><table id=\"sales\">\n  <tr><td>Beer</td></tr>\n</table></body></html>";
    String payload = "{\"raw\":\"" + scrapedHtml.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";

    RawRecord saved =
        rawRecordRepository.saveAndFlush(
            new RawRecord(
                SourceSystem.LIGHTSPEED,
                FetchMethod.SCRAPE,
                "text/html",
                payload,
                "connector:lightspeed-backoffice",
                FETCHED_AT));

    RawRecord reloaded = rawRecordRepository.findById(saved.getId()).orElseThrow();

    assertThat(innerRawText(reloaded.getPayload())).isEqualTo(scrapedHtml);
  }

  private static RawRecord csvRecord(String csv, Instant fetchedAt) {
    return new RawRecord(
        SourceSystem.COOKING_THE_BOOKS,
        FetchMethod.CSV_EXPORT,
        "text/csv",
        "{\"raw\":\"" + csv.replace("\n", "\\n") + "\"}",
        "connector:ctb-invoice-export",
        fetchedAt);
  }

  private static String innerRawText(String payload) {
    try {
      JsonNode node = new ObjectMapper().readTree(payload);
      return node.get("raw").asText();
    } catch (Exception e) {
      throw new AssertionError("payload was not valid JSON: " + payload, e);
    }
  }
}
