package com.goldys.platform.query;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.query.DatasetDescriptor;
import com.goldys.platform.semantic.query.FieldDescriptor;
import com.goldys.platform.semantic.query.QueryErrorCode;
import com.goldys.platform.semantic.query.QueryException;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.QueryResult;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The generic relational query entry point: compiles a plan (validation, authorization, fanout and
 * complexity limits) and executes it read-only, returning typed results with schema and provenance
 * metadata. A single generic interface serves every business question — no bespoke query method.
 */
@Service
public class RelationalQueryService {
  private static final Logger log = LoggerFactory.getLogger(RelationalQueryService.class);

  private final DatasetRegistry registry;
  private final QueryCompiler compiler;
  private final QueryExecutor executor;
  private final PermissionService permissions;

  public RelationalQueryService(
      DatasetRegistry registry,
      QueryCompiler compiler,
      QueryExecutor executor,
      PermissionService permissions) {
    this.registry = registry;
    this.compiler = compiler;
    this.executor = executor;
    this.permissions = permissions;
  }

  public QueryResult run(UserRole role, QueryPlan plan) {
    QueryCompiler.CompiledSql compiled = compiler.compile(role, plan);
    List<List<Object>> rows = executor.execute(compiled.sql(), compiled.params());
    boolean truncated = rows.size() >= compiled.limit();
    boolean currentFact =
        compiled.datasets().stream()
            .map(registry::dataset)
            .anyMatch(DatasetDescriptor::currentFact);
    QueryResult.Semantics semantics =
        new QueryResult.Semantics(
            currentFact, compiled.grain(), compiled.readMode(), compiled.datasets(), Instant.now());
    // Audit the resolved plan (never the result rows or private values).
    log.info(
        "relational query dataset={} columns={} rows={} datasets={}",
        plan.dataset(),
        compiled.columns().size(),
        rows.size(),
        compiled.datasets());
    return new QueryResult(
        compiled.columns(),
        rows,
        new QueryResult.Pagination(compiled.limit(), compiled.offset(), truncated),
        semantics);
  }

  /** The datasets the role may read, hiding any it may not. */
  public List<DatasetDescriptor> datasets(UserRole role) {
    return registry.datasets().stream()
        .filter(
            d ->
                permissions.isAllowed(role, new ResourceKey(d.permission()), PermissionAction.READ))
        .toList();
  }

  /** A dataset's fields, denied explicitly when the role cannot read the dataset. */
  public List<FieldDescriptor> fields(UserRole role, String datasetId) {
    DatasetDescriptor d = registry.dataset(datasetId);
    if (d == null) {
      throw new QueryException(
          QueryErrorCode.UNKNOWN_DATASET,
          "Unknown dataset: " + datasetId,
          java.util.Map.of("identifier", datasetId));
    }
    permissions.require(role, new ResourceKey(d.permission()), PermissionAction.READ);
    return d.fields().stream()
        .filter(
            f -> {
              String resource = f.permission() != null ? f.permission() : d.permission();
              return permissions.isAllowed(role, new ResourceKey(resource), PermissionAction.READ);
            })
        .toList();
  }
}
