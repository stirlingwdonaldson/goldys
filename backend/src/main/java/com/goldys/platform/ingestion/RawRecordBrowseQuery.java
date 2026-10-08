package com.goldys.platform.ingestion;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.RawFilter;
import com.goldys.platform.semantic.RawRecordDetail;
import com.goldys.platform.semantic.RawRecordSummary;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Read-only browse access to the raw ledger for the data explorer: a paged, filterable metadata
 * list (never payload bytes) plus a payload-by-id detail. Returns {@code semantic} records so
 * callers outside this package never touch the package-private {@link RawRecord}.
 */
@Service
public class RawRecordBrowseQuery {
  private static final int MAX_PAGE_SIZE = 200;
  private final RawRecordRepository records;

  public RawRecordBrowseQuery(RawRecordRepository records) {
    this.records = records;
  }

  public DataPage<RawRecordSummary> list(RawFilter filter, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    var result =
        records.findAll(
            spec(filter),
            PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "fetchedAt")));
    return new DataPage<>(
        result.getContent().stream().map(RawRecordBrowseQuery::toSummary).toList(),
        result.getTotalElements(),
        page,
        safeSize);
  }

  public RawRecordDetail byId(UUID id) {
    RawRecord r =
        records.findById(id).orElseThrow(() -> new java.util.NoSuchElementException(id.toString()));
    RawRecordSummary summary = toSummary(r);
    byte[] bytes = r.payloadBytes();
    String text = new String(bytes, encoding(r.characterEncoding()));
    boolean isJson = isJsonText(text);
    // Byte-faithful: JSON is returned as decoded text; anything else is base64 of the exact bytes
    // so the stored sha-256 remains verifiable by the operator.
    String payload = isJson ? text : java.util.Base64.getEncoder().encodeToString(bytes);
    return new RawRecordDetail(summary, payload, isJson, r.payloadSha256());
  }

  private static java.nio.charset.Charset encoding(String name) {
    if (name == null || name.isBlank()) {
      return StandardCharsets.UTF_8;
    }
    try {
      return java.nio.charset.Charset.forName(name);
    } catch (RuntimeException e) {
      return StandardCharsets.UTF_8;
    }
  }

  private static boolean isJsonText(String text) {
    String s = text;
    if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
      s = s.substring(1);
    }
    s = s.stripLeading();
    return s.startsWith("{") || s.startsWith("[");
  }

  private static RawRecordSummary toSummary(RawRecord r) {
    return new RawRecordSummary(
        r.id(),
        r.sourceSystem(),
        r.fetcherIdentity(),
        r.fetchMethod().name(),
        r.contentType(),
        r.characterEncoding(),
        r.fetchedAt(),
        r.payloadByteLength(),
        r.payloadSha256());
  }

  private static Specification<RawRecord> spec(RawFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (filter.sourceSystem() != null) {
        predicates.add(cb.equal(root.get("sourceSystem"), filter.sourceSystem()));
      }
      if (filter.fetcherIdentity() != null) {
        predicates.add(cb.equal(root.get("fetcherIdentity"), filter.fetcherIdentity()));
      }
      FetchMethod method = fetchMethod(filter);
      if (method != null) {
        predicates.add(cb.equal(root.get("fetchMethod"), method));
      }
      if (filter.from() != null) {
        predicates.add(
            cb.greaterThanOrEqualTo(root.get("fetchedAt").as(Instant.class), filter.from()));
      }
      if (filter.to() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("fetchedAt").as(Instant.class), filter.to()));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static FetchMethod fetchMethod(RawFilter filter) {
    if (filter.fetchMethod() == null) {
      return null;
    }
    return FetchMethod.valueOf(filter.fetchMethod());
  }
}
