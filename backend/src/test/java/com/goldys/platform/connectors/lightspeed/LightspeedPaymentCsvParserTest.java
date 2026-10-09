package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LightspeedPaymentCsvParserTest {

  @Test
  void parsesRowsByHeaderName() throws Exception {
    byte[] csv =
        Files.readAllBytes(Path.of("src/test/resources/fixtures/lightspeed/payments_sample.csv"));

    List<LightspeedPayment> payments = new LightspeedPaymentCsvParser().parse(csv);

    assertThat(payments).hasSize(3);
    LightspeedPayment first = payments.get(0);
    assertThat(first.createdDate()).isEqualTo(LocalDate.parse("2026-09-19"));
    assertThat(first.saleNumber()).isEqualTo("SP-100 0919000001");
    assertThat(first.paymentTypeName()).isEqualTo("Tyro");
    assertThat(first.amount()).isEqualByComparingTo("222");
    assertThat(first.tendered()).isEqualByComparingTo("222");
    assertThat(first.paymentCount()).isEqualTo(4);

    // Split tender: the same sale appears again with a different tender.
    assertThat(payments.get(1).saleNumber()).isEqualTo("SP-100 0919000001");
    assertThat(payments.get(1).paymentTypeName()).isEqualTo("Cash");

    // Surcharge, tip and customer fields are preserved.
    LightspeedPayment third = payments.get(2);
    assertThat(third.surcharge()).isEqualByComparingTo("0.10");
    assertThat(third.tip()).isEqualByComparingTo("1.00");
    assertThat(third.customerName()).isEqualTo("Example Customer");
  }

  @Test
  void failsOnMissingRequiredColumn() {
    String csv =
        "Staff Sale Closed Staff Name,Payment Data Created Date\n" + "Example Staff,2026-09-19\n";

    assertThatThrownBy(
            () -> new LightspeedPaymentCsvParser().parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Payments Sale Number");
  }
}
