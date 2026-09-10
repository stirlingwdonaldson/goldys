package com.goldys.platform.raw;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RawRecordRepository extends JpaRepository<RawRecord, UUID> {
}
