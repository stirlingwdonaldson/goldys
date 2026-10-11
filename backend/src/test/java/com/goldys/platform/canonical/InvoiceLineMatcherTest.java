package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.InvoiceLineMatcher.Candidate;
import com.goldys.platform.canonical.InvoiceLineMatcher.Result;
import com.goldys.platform.canonical.InvoiceLineMatcher.Selection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the PDF-line → canonical-line selector, built from the shapes actually observed in
 * the CTB export (synthetic {@code PDF_<hex>} codes, variant/embedded codes, noisy descriptions).
 */
class InvoiceLineMatcherTest {

  private static Selection select(String code, String description, Candidate... candidates) {
    return InvoiceLineMatcher.select(code, description, List.of(candidates));
  }

  @Test
  void matchesOnExactCodeIgnoringCaseAndPunctuation() {
    Selection s =
        select(
            "hamb-008",
            "no description",
            new Candidate("HAMB008", "something else"),
            new Candidate("XXXX", "unrelated"));

    assertThat(s.result()).isEqualTo(Result.MATCH);
    assertThat(s.index()).isEqualTo(0);
  }

  @Test
  void treatsCtbSyntheticCodeAsAbsentAndMatchesOnDescription() {
    // Real case: invoice 4184820, CSVs stock code is a synthetic PDF_<hex> placeholder.
    Selection s =
        select(
            "LIMES",
            "LIMES TRAY",
            new Candidate("PDF_13ACDAD38B042B", "limes tray set price 4 kg price"));

    assertThat(s.result()).isEqualTo(Result.MATCH);
    assertThat(s.index()).isEqualTo(0);
  }

  @Test
  void matchesCodeVariantByUniquePrefix() {
    // Real case: CSV code HAMB008A1c vs PDF code HAMB008.
    Selection s =
        select(
            "HAMB008",
            "BURGER BEEF 150 GM - 54 PCS - ORDER BY BOX",
            new Candidate("HAMB008A1c", "beef burger 150 gm 54 pcs"));

    assertThat(s.result()).isEqualTo(Result.MATCH);
  }

  @Test
  void matchesWhenCsvCodeEmbedsThePdfCode() {
    // Real case: CSV code column carries "STM-RWJ 1KG - PREMIUM RED WINE JUS".
    Selection s =
        select(
            "STM-RWJ",
            "PREMIUM RED WINE JUS",
            new Candidate(
                "STM-RWJ 1KG - PREMIUM RED WINE JUS", "stm rwj 1kg premium red wine jus"));

    assertThat(s.result()).isEqualTo(Result.MATCH);
  }

  @Test
  void matchesWhenDescriptionsDifferOnlyByTrailingNoiseTokens() {
    Selection s =
        select(
            null,
            "BURGER BEEF 150 GM - 54 PCS - ORDER BY BOX",
            new Candidate(null, "beef burger 150 gm 54 pcs"));

    assertThat(s.result()).isEqualTo(Result.MATCH);
  }

  @Test
  void matchesSingleTokenWhenTheExtraTokensAreOnlyPackagingNoise() {
    Selection s = select(null, "CORN", new Candidate(null, "corn x box"));

    assertThat(s.result()).isEqualTo(Result.MATCH);
  }

  @Test
  void doesNotMatchWhenTheOnlySharedTokenIsAPrefixOfADifferentProduct() {
    // "tomatoes" must not silently absorb "cherry tomatoes".
    Selection s = select(null, "TOMATOES", new Candidate(null, "cherry tomatoes box"));

    assertThat(s.result()).isEqualTo(Result.NO_MATCH);
  }

  @Test
  void matchesBySharedSignificantTokensWhenNeitherContainsTheOther() {
    // p={grange,beef,burger,angus}, c={beef,burger,grange,patties}: neither token set contains
    // the other, so only a majority of shared significant tokens can resolve it.
    Selection s =
        select(null, "grange beef burger angus", new Candidate(null, "beef burger grange patties"));

    assertThat(s.result()).isEqualTo(Result.MATCH);
  }

  @Test
  void doesNotMatchOnASingleSharedSignificantToken() {
    Selection s = select(null, "chicken breast", new Candidate(null, "chicken schnitzel"));

    assertThat(s.result()).isEqualTo(Result.NO_MATCH);
  }

  @Test
  void isAmbiguousWhenTwoCandidatesShareTheSameCode() {
    Selection s =
        select(
            "ABC1",
            "x",
            new Candidate("ABC1", "one thing"),
            new Candidate("ABC1", "another thing"));

    assertThat(s.result()).isEqualTo(Result.AMBIGUOUS);
  }

  @Test
  void isAmbiguousWhenTwoCandidatesBothContainTheDescription() {
    Selection s =
        select(
            null,
            "LIMES TRAY",
            new Candidate("A", "limes tray set price 4 kg price"),
            new Candidate("B", "limes tray fresh"));

    assertThat(s.result()).isEqualTo(Result.AMBIGUOUS);
  }

  @Test
  void doesNotMatchANonProductTextLine() {
    Selection s =
        select(
            null,
            "AHA (VIC) Membership - October 2026",
            new Candidate("639215374669252571", "security"));

    assertThat(s.result()).isEqualTo(Result.NO_MATCH);
  }

  @Test
  void noMatchWhenThereAreNoCandidates() {
    assertThat(select("CODE", "desc").result()).isEqualTo(Result.NO_MATCH);
  }

  @Test
  void noMatchWhenCodeAndDescriptionAreBothMissing() {
    Selection s = select(null, null, new Candidate("ABC1", "some product"));

    assertThat(s.result()).isEqualTo(Result.NO_MATCH);
  }
}
