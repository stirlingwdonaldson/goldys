package com.goldys.platform.classification;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProductCategoryAssignmentRepository
    extends JpaRepository<ProductCategoryAssignment, String> {

  long countByStatus(AssignmentStatus status);
}
