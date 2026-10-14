package com.goldys.platform.canonical;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

interface ProductRepository
    extends org.springframework.data.jpa.repository.JpaRepository<Product, java.util.UUID> {
  @Query("select p from Product p where p.name = :name")
  Optional<Product> findByName(String name);
}
