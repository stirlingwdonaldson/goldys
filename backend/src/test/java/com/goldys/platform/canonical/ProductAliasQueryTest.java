package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductAliasQueryTest {

  private final ProductAliasRepository repository = mock(ProductAliasRepository.class);
  private final ProductAliasQuery query = new ProductAliasQuery(repository);

  @Test
  void lookupCanonicalResolvesAlias() {
    ProductAlias alias =
        ProductAlias.create(
            "baba ganoush", "eggplant dip", "MANUAL", Instant.parse("2026-10-13T00:00:00Z"));
    when(repository.findByAliasOrderByCanonicalNameAsc("eggplant dip")).thenReturn(List.of(alias));

    assertThat(query.lookupCanonical("eggplant dip")).contains("baba ganoush");
  }

  @Test
  void lookupCanonicalReturnsAlphabeticallyFirstCanonicalForMultiAlias() {
    ProductAlias bolognese =
        ProductAlias.create(
            "kids gnocchi bolognese",
            "kids gnocchi",
            "MANUAL",
            Instant.parse("2026-10-13T00:00:00Z"));
    ProductAlias napoli =
        ProductAlias.create(
            "kids gnocchi napoli", "kids gnocchi", "MANUAL", Instant.parse("2026-10-13T00:00:00Z"));
    when(repository.findByAliasOrderByCanonicalNameAsc("kids gnocchi"))
        .thenReturn(List.of(bolognese, napoli));

    assertThat(query.lookupCanonical("kids gnocchi")).contains("kids gnocchi bolognese");
  }

  @Test
  void lookupCanonicalEmptyWhenNoMatches() {
    when(repository.findByAliasOrderByCanonicalNameAsc("missing")).thenReturn(List.of());

    assertThat(query.lookupCanonical("missing")).isEmpty();
  }

  @Test
  void allMapsRowsToViews() {
    ProductAlias alias = mock(ProductAlias.class);
    when(alias.canonicalName()).thenReturn("baba ganoush");
    when(alias.alias()).thenReturn("eggplant dip");
    when(repository.findAll()).thenReturn(List.of(alias));

    List<ProductAliasView> all = query.all();

    assertThat(all).containsExactly(new ProductAliasView("baba ganoush", "eggplant dip"));
  }
}
