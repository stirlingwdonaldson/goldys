package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CtbRevenueParserTest {

  @Test
  void parsesEnvelopeAndConvertsTicksToLocalDate() throws Exception {
    byte[] json =
        Files.readAllBytes(Path.of("src/test/resources/fixtures/ctb/revenues_sample.json"));

    List<CtbRevenue> rows = new CtbRevenueParser().parse(json);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).revenueDate()).isEqualTo(LocalDate.of(2026, 8, 24));
    assertThat(rows.get(0).departmentName()).isEqualTo("Beverage");
    assertThat(rows.get(0).totalSales()).isEqualByComparingTo("4087.478");
    assertThat(rows.get(1).departmentName()).isEqualTo("Food");
  }
}
