package com.goldys.platform.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.QueryResult;
import com.goldys.platform.semantic.query.SavedQueryDetail;
import com.goldys.platform.semantic.query.SavedQueryRevisionView;
import com.goldys.platform.semantic.query.SavedQuerySummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD and re-run for saved query definitions. A saved query stores a versioned {@link QueryPlan};
 * running one re-parses, re-validates and re-authorizes the plan, so a permission change that
 * removed the caller's access invalidates the result rather than leaking it.
 */
@Service
public class SavedQueryService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final SavedQueryRepository repository;
  private final SavedQueryRevisionRepository revisions;
  private final RelationalQueryService queries;
  private final ObjectMapper mapper;

  public SavedQueryService(
      SavedQueryRepository repository,
      SavedQueryRevisionRepository revisions,
      RelationalQueryService queries,
      ObjectMapper mapper) {
    this.repository = repository;
    this.revisions = revisions;
    this.queries = queries;
    this.mapper = mapper;
  }

  @Transactional
  public SavedQueryDetail create(String email, String title, QueryPlan plan) {
    Instant now = CLOCK.instant();
    String json = toJson(plan);
    SavedQuery query = SavedQuery.create(UUID.randomUUID(), email, title, json, now);
    repository.save(query);
    revisions.save(SavedQueryRevision.create(query.id(), 1, json, email, now));
    return detail(query);
  }

  @Transactional(readOnly = true)
  public List<SavedQuerySummary> list(String email) {
    return repository.findByCreatedByOrderByUpdatedAtDesc(email).stream()
        .map(
            q ->
                new SavedQuerySummary(
                    q.id(), q.title(), q.currentRevision(), q.createdAt(), q.updatedAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public SavedQueryDetail get(String email, UUID id) {
    return detail(requireOwned(email, id));
  }

  @Transactional
  public SavedQueryDetail update(String email, UUID id, String title, QueryPlan plan) {
    Instant now = CLOCK.instant();
    SavedQuery query = requireOwned(email, id);
    int next = query.currentRevision() + 1;
    String json = toJson(plan);
    query.update(title, json, next, now);
    repository.save(query);
    revisions.save(SavedQueryRevision.create(id, next, json, email, now));
    return detail(query);
  }

  @Transactional
  public void delete(String email, UUID id) {
    repository.delete(requireOwned(email, id));
  }

  @Transactional(readOnly = true)
  public List<SavedQueryRevisionView> revisions(String email, UUID id) {
    requireOwned(email, id);
    return revisions.findByQueryIdOrderByRevisionDesc(id).stream()
        .map(r -> new SavedQueryRevisionView(r.revision(), r.createdBy(), r.createdAt()))
        .toList();
  }

  @Transactional
  public SavedQueryDetail restore(String email, UUID id, int revision) {
    SavedQuery query = requireOwned(email, id);
    SavedQueryRevision snapshot =
        revisions
            .findByQueryIdAndRevision(id, revision)
            .orElseThrow(() -> new IllegalArgumentException("Unknown revision: " + revision));
    return update(email, id, query.title(), fromJson(snapshot.planJson()));
  }

  /** Re-parses, re-validates and re-authorizes the stored plan, then runs it read-only. */
  public QueryResult run(UserRole role, String email, UUID id) {
    SavedQuery query = requireOwned(email, id);
    return queries.run(role, fromJson(query.planJson()));
  }

  /**
   * Runs a saved query by id for a dashboard viewer, without an ownership check — the plan's
   * operands are still re-authorized for the caller, so a shared dashboard cannot leak data the
   * viewer may not read.
   */
  public QueryResult runForViewer(UserRole role, UUID id) {
    SavedQuery query =
        repository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Unknown saved query: " + id));
    return queries.run(role, fromJson(query.planJson()));
  }

  /** The saved query's title, for a widget header ("" when unknown). */
  public String titleById(UUID id) {
    return repository.findById(id).map(SavedQuery::title).orElse("");
  }

  private SavedQuery requireOwned(String email, UUID id) {
    return repository
        .findByIdAndCreatedBy(id, email)
        .orElseThrow(() -> AccessDeniedException.forResource("saved-query"));
  }

  private SavedQueryDetail detail(SavedQuery q) {
    return new SavedQueryDetail(
        q.id(),
        q.title(),
        q.currentRevision(),
        fromJson(q.planJson()),
        q.createdAt(),
        q.updatedAt());
  }

  private String toJson(QueryPlan plan) {
    try {
      return mapper.writeValueAsString(plan);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid query plan", e);
    }
  }

  private QueryPlan fromJson(String json) {
    try {
      return mapper.readValue(json, QueryPlan.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Stored query plan is invalid", e);
    }
  }
}
