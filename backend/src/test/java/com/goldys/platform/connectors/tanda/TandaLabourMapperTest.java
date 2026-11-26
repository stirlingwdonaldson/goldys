package com.goldys.platform.connectors.tanda;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.goldys.platform.canonical.LabourMeasure;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TandaLabourMapperTest {
  private List<TandaLabourMapper.Page> pages() throws Exception {
    var rows =
        new ObjectMapper()
            .readTree(getClass().getResourceAsStream("/fixtures/tanda/workforce.json"));
    var pages = new ArrayList<TandaLabourMapper.Page>();
    for (var row : rows)
      pages.add(
          new TandaLabourMapper.Page(
              row.path("dataset").asText(), row.path("rows"), UUID.randomUUID()));
    return pages;
  }

  @Test
  void mapsRealShapesDeduplicatesEnvelopesAndKeepsOpenShiftsUnknown() throws Exception {
    var result =
        new TandaLabourMapper()
            .map(
                pages(),
                LocalDate.parse("2026-11-19"),
                LocalDate.parse("2026-12-10"),
                ZoneId.of("Australia/Melbourne"));
    assertThat(result).hasSize(229);
    assertThat(result.stream().map(r -> r.department()).distinct())
        .containsExactlyInAnyOrder(
            "Kitchen", "Bar", "Floor", "Bussy", "FOH Managers", "Kitchen Managers", "Admin");
    assertThat(result.stream().map(r -> r.sourceRecordRef()).distinct().count()).isEqualTo(229);
    var worked = result.stream().filter(r -> r.measure() == LabourMeasure.WORKED).toList();
    assertThat(worked).hasSize(6);
    assertThat(worked.stream().filter(r -> r.actualHours() == null)).hasSize(3);
    assertThat(
            worked.stream()
                .filter(
                    r ->
                        r.labourDate().equals(LocalDate.parse("2026-11-20"))
                            && r.actualHours() != null))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.actualHours()).isEqualByComparingTo("8.5");
              assertThat(r.actualCost()).isEqualByComparingTo("0");
            });
    assertThat(result.stream().filter(r -> r.measure() == LabourMeasure.ROSTER))
        .allSatisfy(
            r -> {
              assertThat(r.actualHours()).isNull();
              assertThat(r.actualCost()).isNull();
            });
  }

  @Test
  void rejectsUnknownTeamsRatherThanGuessingTheirReportingGroup() throws Exception {
    var pages = pages();
    var page =
        pages.stream()
            .filter(p -> p.dataset().equals("schedules") && !p.rows().isEmpty())
            .findFirst()
            .orElseThrow();
    ((ObjectNode) page.rows().get(0)).put("department_id", 999999);
    assertThatThrownBy(
            () ->
                new TandaLabourMapper()
                    .map(
                        pages,
                        LocalDate.parse("2026-11-19"),
                        LocalDate.parse("2026-12-10"),
                        ZoneId.of("Australia/Melbourne")))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("unknown team");
  }
}
