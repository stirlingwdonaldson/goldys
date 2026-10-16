package com.goldys.platform.application;

import com.goldys.platform.auth.UserRole;
import com.goldys.platform.query.SavedQueryService;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.QueryResult;
import com.goldys.platform.semantic.query.SavedQueryDetail;
import com.goldys.platform.semantic.query.SavedQueryRevisionView;
import com.goldys.platform.semantic.query.SavedQuerySummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Use-case facade for saved query definitions, keeping REST delivery off the query package. */
@Service
public class SavedQueryApplicationService {
  private final SavedQueryService queries;

  public SavedQueryApplicationService(SavedQueryService queries) {
    this.queries = queries;
  }

  public SavedQueryDetail create(String email, String title, QueryPlan plan) {
    return queries.create(email, title, plan);
  }

  public List<SavedQuerySummary> list(String email) {
    return queries.list(email);
  }

  public SavedQueryDetail get(String email, UUID id) {
    return queries.get(email, id);
  }

  public SavedQueryDetail update(String email, UUID id, String title, QueryPlan plan) {
    return queries.update(email, id, title, plan);
  }

  public void delete(String email, UUID id) {
    queries.delete(email, id);
  }

  public List<SavedQueryRevisionView> revisions(String email, UUID id) {
    return queries.revisions(email, id);
  }

  public SavedQueryDetail restore(String email, UUID id, int revision) {
    return queries.restore(email, id, revision);
  }

  public QueryResult run(UserRole role, String email, UUID id) {
    return queries.run(role, email, id);
  }
}
