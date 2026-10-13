package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LightspeedProductCatalogParserTest {

  @Test
  void parsesProductRows() throws Exception {
    byte[] csv =
        Files.readAllBytes(
            Path.of("src/test/resources/fixtures/lightspeed/product_catalog_sample.csv"));

    var rows = new LightspeedProductCatalogParser().parse(csv);

    assertThat(rows).hasSize(1);
    LightspeedProduct p = rows.get(0);
    assertThat(p.productId()).isEqualTo(22101650);
    assertThat(p.productName()).isEqualTo("$20 Eggplant Parma Special");
    assertThat(p.category()).isEqualTo("Weekly Food Specials");
    assertThat(p.sellPriceIncTax()).isEqualByComparingTo(new BigDecimal("20.00"));
    assertThat(p.isModifier()).isFalse();
    assertThat(p.reportingGroup()).isEqualTo("Goldy's Food");
  }

  @Test
  void parsesModifierWithBlankOptionalFields() {
    byte[] csv =
        ("ProductID,ProductName,CategoryNames,SellPriceIncTax,IsModifier,ReportingGroup\n"
                + "5,Extra Cheese,,,1,\n")
            .getBytes(StandardCharsets.UTF_8);

    var rows = new LightspeedProductCatalogParser().parse(csv);

    assertThat(rows).hasSize(1);
    LightspeedProduct p = rows.get(0);
    assertThat(p.productId()).isEqualTo(5);
    assertThat(p.productName()).isEqualTo("Extra Cheese");
    assertThat(p.category()).isNull();
    assertThat(p.sellPriceIncTax()).isNull();
    assertThat(p.isModifier()).isTrue();
    assertThat(p.reportingGroup()).isNull();
  }

  @Test
  void missingRequiredColumnFailsLoudly() {
    byte[] csv =
        "\"ProductID\",\"CategoryNames\"\n1,\"Weekly Food Specials\"\n"
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> new LightspeedProductCatalogParser().parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("ProductName");
  }

  @Test
  void emptyCsvFailsLoudly() {
    assertThatThrownBy(() -> new LightspeedProductCatalogParser().parse(new byte[0]))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }
}
