package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class ProductAliasSeederTest {

  private final List<ProductAlias> saved = new ArrayList<>();

  private ProductAliasRepository repository;
  private ProductAliasSeeder seeder;

  @BeforeEach
  void setUp() {
    repository = mock(ProductAliasRepository.class);
    seeder = new ProductAliasSeeder(repository);

    when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              ProductAlias alias = invocation.getArgument(0);
              saved.add(alias);
              return alias;
            });
    when(repository.existsByCanonicalNameAndAlias(anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              String canonicalName = invocation.getArgument(0);
              String alias = invocation.getArgument(1);
              return saved.stream()
                  .anyMatch(
                      candidate ->
                          candidate.canonicalName().equals(canonicalName)
                              && candidate.alias().equals(alias));
            });
  }

  @Test
  void seederNormalizesBothSides() {
    seeder.run(mock(ApplicationArguments.class));

    ProductAlias first = saved.get(0);
    assertThat(first.canonicalName()).isEqualTo(ProductNameKey.normalize("Baba Ganoush"));
    assertThat(first.alias()).isEqualTo(ProductNameKey.normalize("Eggplant Dip"));
    assertThat(first.source()).isEqualTo("MANUAL");
    assertThat(first.createdAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void seederIsIdempotent() {
    seeder.run(mock(ApplicationArguments.class));
    int seeded = saved.size();

    seeder.run(mock(ApplicationArguments.class));

    assertThat(saved).hasSize(seeded);
  }
}
