package com.goldys.platform.classification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategorySuggestionRepository extends JpaRepository<CategorySuggestion, UUID> {

  List<CategorySuggestion> findAllByOrderByDisplayOrderAsc();
}
