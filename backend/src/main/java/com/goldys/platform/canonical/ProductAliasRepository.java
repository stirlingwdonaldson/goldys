package com.goldys.platform.canonical;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductAliasRepository extends JpaRepository<ProductAlias, UUID> {
  boolean existsByCanonicalNameAndAlias(String canonicalName, String alias);

  Optional<ProductAlias> findByAlias(String alias);
}
