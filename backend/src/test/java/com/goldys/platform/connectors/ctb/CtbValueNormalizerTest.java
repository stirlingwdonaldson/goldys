package com.goldys.platform.connectors.ctb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The shared CTB value normalizer must treat every encoding CTB emits as one consistent null. */
class CtbValueNormalizerTest {

  @Test
  void parsesMicrosoftAjaxEpochDate() {
    // Observed: statementDate "/Date(1777471200000)/" pairs with statementDateString "30/04/2026".
    assertEquals(LocalDate.of(2026, 4, 30), CtbValueNormalizer.parseDate("/Date(1777471200000)/"));
  }

  @Test
  void parsesHumanWeekdayDate() {
    assertEquals(
        LocalDate.of(2026, 9, 29), CtbValueNormalizer.parseDate("Tuesday, 29 September 2026"));
    assertEquals(LocalDate.of(2026, 7, 20), CtbValueNormalizer.parseDate("Monday, 20 July 2026"));
  }

  @Test
  void parsesDmyDate() {
    assertEquals(LocalDate.of(2026, 5, 31), CtbValueNormalizer.parseDate("31/05/2026"));
    assertEquals(LocalDate.of(2026, 10, 14), CtbValueNormalizer.parseDate("14/10/2026"));
  }

  @Test
  void parsesDmyDateTime() {
    assertEquals(LocalDate.of(2026, 6, 3), CtbValueNormalizer.parseDate("03/06/2026 10:50"));
    assertEquals(LocalDate.of(2026, 10, 12), CtbValueNormalizer.parseDate("12/10/2026 12:49"));
  }

  @Test
  void parsesEpochMillis() {
    // 1777471200000 ms == the /Date(...) value above, decoded directly.
    assertEquals(LocalDate.of(2026, 4, 30), CtbValueNormalizer.parseEpochMillis(1777471200000L));
  }

  @Test
  void blankAndNullDatesReturnNull() {
    assertNull(CtbValueNormalizer.parseDate(null));
    assertNull(CtbValueNormalizer.parseDate(""));
    assertNull(CtbValueNormalizer.parseDate("   "));
  }

  @Test
  void emptyStringIsTreatedAsNull() {
    assertNull(CtbValueNormalizer.nullIfBlank(""));
    assertNull(CtbValueNormalizer.nullIfBlank("   "));
    assertEquals("abc", CtbValueNormalizer.nullIfBlank("abc"));
  }

  @Test
  void nonPositiveIdsAreSentinel() {
    assertEquals(true, CtbValueNormalizer.isSentinelId(-1));
    assertEquals(true, CtbValueNormalizer.isSentinelId(0));
    assertEquals(true, CtbValueNormalizer.isSentinelId(-4));
    assertEquals(false, CtbValueNormalizer.isSentinelId(38));
  }

  @Test
  void syntheticPdfCodesAreNotCodes() {
    assertNull(CtbValueNormalizer.normalizeStockCode("PDF_1346E98D2AE7D3"));
    assertNull(CtbValueNormalizer.normalizeStockCode("pdf_1346ef2d15f7a7"));
    assertEquals("87882", CtbValueNormalizer.normalizeStockCode("87882"));
    assertEquals("VICDG24", CtbValueNormalizer.normalizeStockCode(" VICDG24 "));
  }

  @Test
  void stripsNulAndControlCharacters() {
    assertEquals(
        "Mon, 05 Oct 2026 from 6", CtbValueNormalizer.clean("Mon, 05 Oct 2026 from 6\u0000"));
    assertEquals("ZUCCHINI X KILO", CtbValueNormalizer.clean("  ZUCCHINI X KILO  "));
    assertNull(CtbValueNormalizer.clean(null));
  }
}
