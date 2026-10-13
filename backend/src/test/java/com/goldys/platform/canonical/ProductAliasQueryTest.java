package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductAliasQueryTest {

  private final ProductAliasRepository repository = mock(ProductAliasRepository.class);
  private final ProductAliasQuery query = new ProductAliasQuery(repository);

  @Test
  void lookupCanonicalResolvesAlias() {
    ProductAlias alias =
        ProductAlias.create(
            "baba ganoush", "eggplant dip", "MANUAL", Instant.parse("2026-10-13T00:00:00Z"));
    when(repository.findByAlias("eggplant dip")).thenReturn(Optional.of(alias));

    assertThat(query.lookupCanonical("eggplant dip")).contains("baba ganoush");
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
