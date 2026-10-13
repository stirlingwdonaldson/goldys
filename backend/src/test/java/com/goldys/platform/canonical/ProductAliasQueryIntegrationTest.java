package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProductAliasQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ProductAliasQuery query;
  @Autowired ProductAliasRepository repository;
  @Autowired ProductAliasSeeder seeder;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table product_alias");
    seeder.run(mock(ApplicationArguments.class));
  }

  @Test
  void multiCanonicalAliasResolvesToAlphabeticallyFirstCanonical() {
    assertThat(query.lookupCanonical("kids gnocchi")).contains("kids gnocchi bolognese");
  }

  @Test
  void multiCanonicalAliasExposesBothCandidatesInDeterministicOrder() {
    assertThat(repository.findByAliasOrderByCanonicalNameAsc("kids gnocchi"))
        .extracting(ProductAlias::canonicalName)
        .containsExactly("kids gnocchi bolognese", "kids gnocchi napoli");
  }
}
