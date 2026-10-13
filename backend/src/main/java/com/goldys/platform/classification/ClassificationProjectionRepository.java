package com.goldys.platform.classification;

import org.springframework.data.jpa.repository.JpaRepository;

interface ClassificationProjectionRepository
    extends JpaRepository<ClassificationProjection, ClassificationProjection.Id> {}
