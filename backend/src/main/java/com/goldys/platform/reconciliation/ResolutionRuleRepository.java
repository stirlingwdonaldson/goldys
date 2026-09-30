package com.goldys.platform.reconciliation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ResolutionRuleRepository extends JpaRepository<ResolutionRule, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from ResolutionRule r where r.entityType = :entityType "
          + "and r.fieldKey = :fieldKey and r.supersededAt is null")
  Optional<ResolutionRule> lockCurrent(String entityType, String fieldKey);

  @Query(
      "select r from ResolutionRule r where r.entityType = :entityType "
          + "and r.fieldKey = :fieldKey and r.supersededAt is null")
  Optional<ResolutionRule> findCurrent(String entityType, String fieldKey);

  List<ResolutionRule> findAllByOrderByRecordedAtDesc();

  List<ResolutionRule> findAllBySupersededAtIsNullOrderByRecordedAtDesc();

  Optional<ResolutionRule> findFirstByOrderByRecordedAtDesc();
}
