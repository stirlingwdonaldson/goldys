package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenTableMenuItemsCsvParserTest {

  private final OpenTableMenuItemsCsvParser parser = new OpenTableMenuItemsCsvParser();

  private byte[] fixture() throws Exception {
    return Files.readAllBytes(
        Path.of("src/test/resources/fixtures/opentable/menu_items_sample.csv"));
  }

  @Test
  void mapsIdentityAndPrice() throws Exception {
    List<OpenTableMenuItem> rows = parser.parse(fixture());

    assertThat(rows).hasSize(3);

    OpenTableMenuItem tarot = rows.get(0);
    assertThat(tarot.invariantId()).isEqualTo("82f5decd-17cd-434a-b708-2b4e87978b21");
    assertThat(tarot.itemId()).isEqualTo("ffedfaa2-a648-4430-9922-6bb934bc22e0");
    assertThat(tarot.name()).isEqualTo("Tarot Grenache");
    assertThat(tarot.price()).isEqualByComparingTo(new BigDecimal("72.00"));

    // A blank Price column maps to null rather than failing the parse.
    OpenTableMenuItem lager = rows.get(1);
    assertThat(lager.name()).isEqualTo("Goldy's! 'One Off The Wood' Lager");
    assertThat(lager.price()).isNull();

    OpenTableMenuItem sangiovese = rows.get(2);
    assertThat(sangiovese.price()).isEqualByComparingTo(new BigDecimal("78.00"));
  }

  @Test
  void stripsBom() {
    String csv =
        "Items,Price,Item ID,Invariant ID\n"
            + "Tarot Grenache,A$72.00,ffedfaa2-a648-4430-9922-6bb934bc22e0,"
            + "82f5decd-17cd-434a-b708-2b4e87978b21\n";
    byte[] withBom = prependBom(csv.getBytes(StandardCharsets.UTF_8));

    List<OpenTableMenuItem> rows = parser.parse(withBom);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).name()).isEqualTo("Tarot Grenache");
    assertThat(rows.get(0).price()).isEqualByComparingTo(new BigDecimal("72.00"));
  }

  @Test
  void missingColumnThrows() {
    String csv = "Items,Item ID,Invariant ID\nTarot Grenache,ffedfaa2,82f5decd\n";

    assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }

  private static byte[] prependBom(byte[] csv) {
    byte[] out = new byte[csv.length + 3];
    out[0] = (byte) 0xEF;
    out[1] = (byte) 0xBB;
    out[2] = (byte) 0xBF;
    System.arraycopy(csv, 0, out, 3, csv.length);
    return out;
  }
}
