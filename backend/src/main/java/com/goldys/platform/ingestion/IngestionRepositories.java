package com.goldys.platform.ingestion;

import com.goldys.platform.semantic.RawRecordSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence boundaries for the ingestion ledger.
 *
 * <p>Package-private on purpose: other modules read ingestion state through services in this
 * package, not by holding JPA entities.
 */
interface IngestionRunRepository extends JpaRepository<IngestionRun, UUID> {
  List<IngestionRun> findAllByOrderByStartedAtDesc();

  List<IngestionRun> findByStartedAtGreaterThanEqual(Instant startedAt);

  Optional<IngestionRun> findFirstBySourceSystemOrderByStartedAtDesc(String sourceSystem);

  List<IngestionRun> findByStatus(IngestionStatus status);

  /**
   * The latest run per source system, in one query. Replaces loading the entire ledger and deduping
   * in memory (the previous behaviour, which grew with ingestion history).
   */
  @Query(
      "select r from IngestionRun r where r.startedAt = "
          + "(select max(r2.startedAt) from IngestionRun r2 where r2.sourceSystem = r.sourceSystem)")
  List<IngestionRun> latestPerSource();

  /**
   * The latest run per (source system, connector name), so a successful CSV push does not mask a
   * partial web pull for the same source.
   */
  @Query(
      "select r from IngestionRun r where r.startedAt = "
          + "(select max(r2.startedAt) from IngestionRun r2 "
          + "where r2.sourceSystem = r.sourceSystem and r2.connectorName = r.connectorName)")
  List<IngestionRun> latestPerConnector();

  /**
   * Status distribution for the ingestion-health completeness calculation, excluding one raw-only
   * connector (the PDF-enrichment writes) that is not data delivery.
   */
  @Query(
      "select new com.goldys.platform.ingestion.StatusCount(r.status, count(r)) "
          + "from IngestionRun r where r.connectorName <> :excludedConnector group by r.status")
  List<StatusCount> statusCountsExcludingConnector(
      @Param("excludedConnector") String excludedConnector);

  /** Mean run duration (seconds) of failed/partial runs, for the time-to-detect metric. */
  @Query(
      value =
          "select avg(extract(epoch from (completed_at - started_at))) from ingestion_run "
              + "where status in ('FAILED', 'PARTIAL') and completed_at is not null",
      nativeQuery = true)
  Double avgFailedDurationSeconds();
}

interface RawRecordRepository
    extends JpaRepository<RawRecord, UUID>, JpaSpecificationExecutor<RawRecord> {
  List<RawRecord> findByIngestionRunId(UUID ingestionRunId);

  List<RawRecord> findBySourceSystem(String sourceSystem);

  List<RawRecord> findBySourceSystemAndContentType(String sourceSystem, String contentType);

  List<RawRecord> findBySourceSystemAndFetcherIdentity(String sourceSystem, String fetcherIdentity);

  /**
   * Id and hash of the latest payload for one dataset window. A closed interface projection, so
   * only those two columns are selected and {@code payload_bytes} is never loaded. Backed by {@code
   * idx_raw_record_source_fetcher_ref} (V53).
   */
  Optional<RawRecordDigest>
      findFirstBySourceSystemAndFetcherIdentityAndSourceRecordRefOrderByFetchedAtDesc(
          String sourceSystem, String fetcherIdentity, String sourceRecordRef);

  /**
   * Raw-record ids of one fetcher identity, deliberately excluding {@code payload_bytes}: replay
   * backfills fetch the bytes one record at a time via {@code findById}, so a dataset of hundreds
   * of MB is never materialised as one list.
   */
  @Query(
      "select r.id from RawRecord r where r.fetcherIdentity = :fetcherIdentity order by r.fetchedAt")
  List<UUID> findIdsByFetcherIdentity(@Param("fetcherIdentity") String fetcherIdentity);

  /**
   * Paged metadata list, deliberately selecting the summary columns only. Never loads {@code
   * payload_bytes}: some source payloads are hundreds of MB, and fetching them into a list query
   * OOMs both the JDBC result set and the JVM heap.
   */
  @Query(
      """
      select new com.goldys.platform.semantic.RawRecordSummary(
          r.id, r.sourceSystem, r.fetcherIdentity, str(r.fetchMethod), r.contentType,
          r.characterEncoding, r.fetchedAt, r.payloadByteLength, r.payloadSha256)
      from RawRecord r
      where (:source is null or r.sourceSystem = :source)
        and (:fetcher is null or r.fetcherIdentity = :fetcher)
        and (:method is null or r.fetchMethod = :method)
        and r.fetchedAt >= :from
        and r.fetchedAt <= :to
      order by r.fetchedAt desc
      """)
  Page<RawRecordSummary> findSummaries(
      @Param("source") String source,
      @Param("fetcher") String fetcher,
      @Param("method") FetchMethod method,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}

interface IngestionStageRepository extends JpaRepository<IngestionStage, UUID> {
  List<IngestionStage> findByIngestionRunId(UUID ingestionRunId);
}

/** A status → count aggregate used by the ingestion-health read model. */
record StatusCount(IngestionStatus status, long count) {}

/** Id and SHA-256 of one stored raw payload, without its bytes (Spring Data projection). */
interface RawRecordDigest {
  UUID getId();

  String getPayloadSha256();
}
