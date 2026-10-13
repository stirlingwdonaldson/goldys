package com.goldys.platform.classification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

  Optional<ProductCategory> findByName(String name);

  List<ProductCategory> findAllByOrderByGroupNameAscDisplayOrderAscNameAsc();
}
