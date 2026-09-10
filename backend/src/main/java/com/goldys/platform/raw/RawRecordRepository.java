package com.goldys.platform.raw;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawRecordRepository extends JpaRepository<RawRecord, UUID> {}
