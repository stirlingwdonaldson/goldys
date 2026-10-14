package com.goldys.platform.classification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReviewClassificationRunRepository extends JpaRepository<ReviewClassificationRun, UUID> {}
