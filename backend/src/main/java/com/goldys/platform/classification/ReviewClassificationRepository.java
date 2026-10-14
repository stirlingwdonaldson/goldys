package com.goldys.platform.classification;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReviewClassificationRepository
    extends JpaRepository<ReviewClassificationEntity, String> {}
