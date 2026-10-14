package com.goldys.platform.application;

import com.goldys.platform.auth.UserRole;
import com.goldys.platform.query.RelationalQueryService;
import com.goldys.platform.semantic.query.DatasetDescriptor;
import com.goldys.platform.semantic.query.FieldDescriptor;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.QueryResult;
import com.goldys.platform.semantic.query.RelationshipDescriptor;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Use-case facade for the generic relational query path. Authorization is operand-level (inside the
 * query compiler), so this service is a thin boundary that keeps the REST delivery from reaching
 * the query package directly.
 */
@Service
public class RelationalQueryApplicationService {
  private final RelationalQueryService queries;

  public RelationalQueryApplicationService(RelationalQueryService queries) {
    this.queries = queries;
  }

  public QueryResult run(UserRole role, QueryPlan plan) {
    return queries.run(role, plan);
  }

  public List<DatasetDescriptor> datasets(UserRole role) {
    return queries.datasets(role);
  }

  public List<FieldDescriptor> fields(UserRole role, String datasetId) {
    return queries.fields(role, datasetId);
  }

  public List<RelationshipDescriptor> relationships(UserRole role) {
    return queries.relationships(role);
  }
}
