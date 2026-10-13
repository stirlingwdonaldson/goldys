package com.goldys.platform.canonical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductAliasRepository extends JpaRepository<ProductAlias, UUID> {
  boolean existsByCanonicalNameAndAlias(String canonicalName, String alias);

  List<ProductAlias> findByAliasOrderByCanonicalNameAsc(String alias);
}
