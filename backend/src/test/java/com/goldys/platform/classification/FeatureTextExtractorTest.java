package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.goldys.platform.canonical.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureTextExtractorTest {
  @Test
  void retains_conflicting_observations_and_holds_multiple_source_identities() {
    var sales = mock(CanonicalProductSalesQuery.class);
    var items = mock(CanonicalSaleItemQuery.class);
    var links = mock(CanonicalSaleRecipeLinkQuery.class);
    var evidence = mock(ClassificationEvidenceQuery.class);
    when(sales.distinctProductNameKeys()).thenReturn(List.of("coke zero mixer", "porterhouse"));
    when(items.currentSaleItemIdentities())
        .thenReturn(
            List.of(
                new SaleItemIdentityView("Porterhouse", "Uncategorised"),
                new SaleItemIdentityView("Porterhouse", "FOOD MENU")));
    when(links.currentLinks())
        .thenReturn(
            List.of(
                new SaleRecipeLinkView(-1, "", "", "Coke Zero - Mixer", false, -207, null),
                new SaleRecipeLinkView(
                    292,
                    "No Sugar Cola Mixer",
                    "No Sugar Cola",
                    "Coke Zero - Mixer",
                    true,
                    213,
                    null)));
    when(evidence.currentEvidence())
        .thenReturn(
            List.of(
                new ClassificationEvidenceQuery.Evidence(
                    "Pint - Philter XPA", "CTB:0", "one", "source one"),
                new ClassificationEvidenceQuery.Evidence(
                    "Pint - Philter XPA", "CTB:0", "two", "source two"),
                new ClassificationEvidenceQuery.Evidence(
                    "Collision", "receipt", "", "two POS IDs", true)));
    var extractor = new FeatureTextExtractor(sales, items, links, evidence);
    var candidates = extractor.candidates();
    assertThat(candidates.get("porterhouse").text()).contains("FOOD MENU", "Uncategorised");
    assertThat(candidates.get("coke zero mixer").text()).contains("recipe=-1", "recipe=292");
    assertThat(candidates.get("coke zero mixer").ambiguous()).isTrue();
    assertThat(candidates.get("pint philter xpa").ambiguous()).isTrue();
    assertThat(candidates.get("porterhouse").ambiguous()).isFalse();
    assertThat(candidates.get("collision").ambiguous()).isTrue();
  }
}
