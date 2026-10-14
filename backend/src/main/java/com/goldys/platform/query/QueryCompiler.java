package com.goldys.platform.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.query.AggregateFunction;
import com.goldys.platform.semantic.query.DatasetDescriptor;
import com.goldys.platform.semantic.query.FieldDescriptor;
import com.goldys.platform.semantic.query.FieldKind;
import com.goldys.platform.semantic.query.FieldType;
import com.goldys.platform.semantic.query.FilterNode;
import com.goldys.platform.semantic.query.FilterOp;
import com.goldys.platform.semantic.query.Grain;
import com.goldys.platform.semantic.query.GroupByItem;
import com.goldys.platform.semantic.query.OrderByItem;
import com.goldys.platform.semantic.query.Predicate;
import com.goldys.platform.semantic.query.QueryErrorCode;
import com.goldys.platform.semantic.query.QueryException;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.ReadMode;
import com.goldys.platform.semantic.query.RelationshipDescriptor;
import com.goldys.platform.semantic.query.ResultColumn;
import com.goldys.platform.semantic.query.SelectItem;
import com.goldys.platform.semantic.query.SortDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves a {@link QueryPlan} against the registered catalogue and compiles it to parameterised
 * SQL. Identifiers are always resolved from the catalogue (never user text) and values are always
 * bind parameters, so the plan cannot become uncontrolled SQL. Enforces operand-level
 * authorization, type checks, fanout safety and complexity limits before any SQL is built.
 */
@Component
public class QueryCompiler {

  static final int MAX_LIMIT = 1000;
  static final int MAX_JOINS = 2;
  static final int MAX_SELECT = 20;
  static final int MAX_GROUP = 20;
  static final int MAX_ORDER = 20;
  static final int MAX_FILTER_DEPTH = 8;

  private final DatasetRegistry registry;
  private final PermissionService permissions;

  public QueryCompiler(DatasetRegistry registry, PermissionService permissions) {
    this.registry = registry;
    this.permissions = permissions;
  }

  /** A resolved, qualified field reference. */
  private record Field(String dataset, String alias, String column, FieldType type, String unit) {}

  private record ResolvedSelect(Field field, AggregateFunction aggregate, boolean distinct) {}

  private record GroupField(Field field, Grain grain) {}

  /** Compiled SQL with typed bind parameters, result metadata, and pagination. */
  public record CompiledSql(
      String sql,
      List<Object> params,
      List<ResultColumn> columns,
      String grain,
      ReadMode readMode,
      List<String> datasets,
      int limit,
      int offset) {}

  public CompiledSql compile(UserRole role, QueryPlan plan) {
    if (plan.version() != 1) {
      throw new QueryException(QueryErrorCode.UNKNOWN_VERSION, "Unsupported query plan version");
    }
    if (plan.limit() <= 0 || plan.limit() > MAX_LIMIT) {
      throw new QueryException(QueryErrorCode.LIMIT_EXCEEDED, "limit must be 1.." + MAX_LIMIT);
    }
    if (plan.offset() < 0) {
      throw new QueryException(QueryErrorCode.INVALID_PLAN, "offset must be >= 0");
    }

    DatasetDescriptor primary = registry.dataset(plan.dataset());
    if (primary == null) {
      throw new QueryException(
          QueryErrorCode.UNKNOWN_DATASET,
          "Unknown dataset: " + plan.dataset(),
          Map.of("identifier", plan.dataset()));
    }

    // Resolve the query's datasets and aliases (primary d0, joins d1..).
    Map<String, DatasetDescriptor> datasets = new LinkedHashMap<>();
    Map<String, String> aliases = new LinkedHashMap<>();
    datasets.put(primary.id(), primary);
    aliases.put(primary.id(), "d0");
    List<RelationshipDescriptor> resolvedJoins = new ArrayList<>();
    if (plan.joins().size() > MAX_JOINS) {
      throw new QueryException(
          QueryErrorCode.COMPLEXITY_EXCEEDED, "Too many joins (max " + MAX_JOINS + ")");
    }
    String current = primary.id();
    for (String joinId : plan.joins()) {
      RelationshipDescriptor rel = registry.relationship(joinId);
      if (rel == null || !rel.from().equals(current)) {
        throw new QueryException(
            QueryErrorCode.UNKNOWN_RELATIONSHIP,
            "Unknown relationship: " + joinId,
            Map.of("identifier", joinId));
      }
      DatasetDescriptor to = registry.dataset(rel.to());
      datasets.put(to.id(), to);
      aliases.put(to.id(), "d" + (resolvedJoins.size() + 1));
      resolvedJoins.add(rel);
      current = rel.to();
    }

    // Resolve select fields.
    List<SelectItem> select = plan.select();
    if (select.isEmpty() || select.size() > MAX_SELECT) {
      throw new QueryException(
          QueryErrorCode.INVALID_PLAN, "select must have 1.." + MAX_SELECT + " items");
    }
    List<ResolvedSelect> resolvedSelect = new ArrayList<>();
    boolean anyAggregate = false;
    for (SelectItem item : select) {
      Field f = resolveField(item.field(), datasets, aliases);
      if (item.aggregate() != null) {
        anyAggregate = true;
        boolean numericOnly =
            item.aggregate() == AggregateFunction.SUM || item.aggregate() == AggregateFunction.AVG;
        if (numericOnly && f.type() != FieldType.DECIMAL && f.type() != FieldType.INTEGER) {
          throw new QueryException(
              QueryErrorCode.TYPE_MISMATCH,
              "aggregate " + item.aggregate() + " requires a numeric field",
              Map.of("field", item.field()));
        }
      }
      resolvedSelect.add(new ResolvedSelect(f, item.aggregate(), item.distinct()));
    }

    // Resolve group-by fields (dimensions only, with optional time grain).
    if (plan.groupBy().size() > MAX_GROUP) {
      throw new QueryException(QueryErrorCode.COMPLEXITY_EXCEEDED, "Too many group-by fields");
    }
    List<GroupField> groupBy = new ArrayList<>();
    for (GroupByItem item : plan.groupBy()) {
      Field f = resolveField(item.field(), datasets, aliases);
      FieldDescriptor fd = descriptorOf(datasets, item.field());
      if (fd != null && fd.kind() == FieldKind.MEASURE) {
        throw new QueryException(
            QueryErrorCode.TYPE_MISMATCH,
            "cannot group by a measure",
            Map.of("field", item.field()));
      }
      if (item.grain() != Grain.DAY && f.type() != FieldType.DATE) {
        throw new QueryException(
            QueryErrorCode.TYPE_MISMATCH,
            "time grain " + item.grain() + " requires a DATE field",
            Map.of("field", item.field()));
      }
      groupBy.add(new GroupField(f, item.grain()));
    }
    if (plan.orderBy().size() > MAX_ORDER) {
      throw new QueryException(QueryErrorCode.COMPLEXITY_EXCEEDED, "Too many order-by fields");
    }

    // Group-by consistency with aggregation.
    if (anyAggregate) {
      for (ResolvedSelect s : resolvedSelect) {
        if (s.aggregate() == null && !containsColumn(groupBy, s.field())) {
          throw new QueryException(
              QueryErrorCode.INVALID_PLAN,
              "non-aggregate field '" + s.field().column() + "' must be in groupBy",
              Map.of("field", s.field().column()));
        }
      }
    } else if (!groupBy.isEmpty()) {
      throw new QueryException(
          QueryErrorCode.INVALID_PLAN, "groupBy requires an aggregate in select");
    }

    // Fanout protection: a SUM/AVG of a "one"-side measure across a fanout join must be distinct.
    for (ResolvedSelect s : resolvedSelect) {
      if (s.aggregate() == AggregateFunction.SUM || s.aggregate() == AggregateFunction.AVG) {
        for (RelationshipDescriptor rel : resolvedJoins) {
          if (rel.fanoutRisk() && rel.from().equals(s.field().dataset()) && !s.distinct()) {
            throw new QueryException(
                QueryErrorCode.FANOUT_RISK,
                "aggregating '"
                    + s.field().column()
                    + "' across the 1:N join '"
                    + rel.id()
                    + "' would double-count; set distinct",
                Map.of("field", s.field().column(), "relationship", rel.id()));
          }
        }
      }
    }

    // Compile filter (depth-bounded, type-checked, value-coerced).
    List<Object> params = new ArrayList<>();
    String where = null;
    if (plan.filter() != null) {
      where = compileFilter(plan.filter(), datasets, aliases, params, 0);
    }
    List<String> currentFact = new ArrayList<>();
    for (Map.Entry<String, DatasetDescriptor> e : datasets.entrySet()) {
      if (e.getValue().currentFact()) {
        currentFact.add(aliases.get(e.getKey()) + ".superseded_at IS NULL");
      }
    }
    if (!currentFact.isEmpty()) {
      where =
          where == null
              ? String.join(" AND ", currentFact)
              : where + " AND " + String.join(" AND ", currentFact);
    }

    // Operand-level authorization.
    authorize(role, primary, datasets, resolvedJoins, select);

    // Compile SQL.
    String sql =
        buildSql(
            primary,
            datasets,
            aliases,
            resolvedJoins,
            resolvedSelect,
            where,
            groupBy,
            plan.orderBy(),
            params,
            plan.limit(),
            plan.offset());

    List<ResultColumn> columns = new ArrayList<>();
    for (ResolvedSelect s : resolvedSelect) {
      columns.add(new ResultColumn(s.field().column(), s.field().type(), s.field().unit()));
    }
    return new CompiledSql(
        sql,
        params,
        columns,
        primary.grain(),
        primary.readMode(),
        new ArrayList<>(datasets.keySet()),
        plan.limit(),
        plan.offset());
  }

  private String buildSql(
      DatasetDescriptor primary,
      Map<String, DatasetDescriptor> datasets,
      Map<String, String> aliases,
      List<RelationshipDescriptor> joins,
      List<ResolvedSelect> select,
      String where,
      List<GroupField> groupBy,
      List<OrderByItem> orderBy,
      List<Object> params,
      int limit,
      int offset) {
    List<String> selectExprs = new ArrayList<>();
    for (ResolvedSelect s : select) {
      selectExprs.add(selectExpression(s, groupBy));
    }
    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectExprs));
    sql.append(" FROM ").append(primary.table()).append(" ").append(aliases.get(primary.id()));
    for (RelationshipDescriptor rel : joins) {
      String toAlias = aliases.get(rel.to());
      String fromAlias = aliases.get(rel.from());
      sql.append(" JOIN ")
          .append(registry.dataset(rel.to()).table())
          .append(" ")
          .append(toAlias)
          .append(" ON ");
      for (int i = 0; i < rel.joinKeys().size(); i++) {
        if (i > 0) {
          sql.append(" AND ");
        }
        RelationshipDescriptor.JoinKey k = rel.joinKeys().get(i);
        sql.append(fromAlias)
            .append('.')
            .append(k.fromField())
            .append(" = ")
            .append(toAlias)
            .append('.')
            .append(k.toField());
      }
    }
    if (where != null) {
      sql.append(" WHERE ").append(where);
    }
    if (!groupBy.isEmpty()) {
      sql.append(" GROUP BY ");
      for (int i = 0; i < groupBy.size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        GroupField g = groupBy.get(i);
        sql.append(groupExpression(g));
      }
    }
    if (!orderBy.isEmpty()) {
      sql.append(" ORDER BY ");
      for (int i = 0; i < orderBy.size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        OrderByItem o = orderBy.get(i);
        Field orderField = resolveField(o.field(), datasets, aliases);
        Integer idx = null;
        for (int j = 0; j < select.size(); j++) {
          if (select.get(j).field().column().equals(orderField.column())) {
            idx = j;
            break;
          }
        }
        if (idx == null) {
          throw new QueryException(
              QueryErrorCode.UNKNOWN_FIELD,
              "order-by field is not selected: " + o.field(),
              Map.of("field", o.field()));
        }
        sql.append(selectExprs.get(idx))
            .append(' ')
            .append(o.direction() == SortDirection.DESC ? "DESC" : "ASC");
      }
    }
    sql.append(" LIMIT ? OFFSET ?");
    params.add(limit);
    params.add(offset);
    return sql.toString();
  }

  private static boolean containsColumn(List<GroupField> fields, Field target) {
    return fields.stream().anyMatch(f -> f.field().column().equals(target.column()));
  }

  /** The SQL expression for a selected field, applying grain and aggregation. */
  private static String selectExpression(ResolvedSelect s, List<GroupField> groupBy) {
    String col = s.field().alias() + "." + s.field().column();
    if (s.aggregate() == null) {
      return grainFor(s, groupBy) == Grain.MONTH ? "date_trunc('month', " + col + ")" : col;
    }
    if (s.aggregate() == AggregateFunction.COUNT) {
      return "COUNT(" + col + ")";
    }
    if (s.aggregate() == AggregateFunction.COUNT_DISTINCT) {
      return "COUNT(DISTINCT " + col + ")";
    }
    return s.aggregate().name() + "(" + (s.distinct() ? "DISTINCT " : "") + col + ")";
  }

  /** The SQL expression for a group-by field, applying a time grain to DATE fields. */
  private static String groupExpression(GroupField g) {
    if (g.grain() == Grain.MONTH && g.field().type() == FieldType.DATE) {
      return "date_trunc('month', " + g.field().alias() + "." + g.field().column() + ")";
    }
    return g.field().alias() + "." + g.field().column();
  }

  private static Grain grainFor(ResolvedSelect s, List<GroupField> groupBy) {
    for (GroupField g : groupBy) {
      if (g.field().column().equals(s.field().column())) {
        return g.grain();
      }
    }
    return Grain.DAY;
  }

  private String compileFilter(
      FilterNode node,
      Map<String, DatasetDescriptor> datasets,
      Map<String, String> aliases,
      List<Object> params,
      int depth) {
    if (depth > MAX_FILTER_DEPTH) {
      throw new QueryException(QueryErrorCode.COMPLEXITY_EXCEEDED, "filter tree too deep");
    }
    if (node.isLeaf()) {
      return compilePredicate(node.predicate(), datasets, aliases, params);
    }
    String logic = node.logic();
    if ("NOT".equals(logic)) {
      if (node.children().size() != 1) {
        throw new QueryException(QueryErrorCode.INVALID_PLAN, "NOT takes one child");
      }
      return "NOT ("
          + compileFilter(node.children().get(0), datasets, aliases, params, depth + 1)
          + ")";
    }
    if (!"AND".equals(logic) && !"OR".equals(logic)) {
      throw new QueryException(QueryErrorCode.INVALID_PLAN, "Unknown filter logic: " + logic);
    }
    if (node.children().isEmpty()) {
      throw new QueryException(QueryErrorCode.INVALID_PLAN, logic + " needs children");
    }
    List<String> parts = new ArrayList<>();
    for (FilterNode child : node.children()) {
      parts.add(compileFilter(child, datasets, aliases, params, depth + 1));
    }
    return "(" + String.join(" " + logic + " ", parts) + ")";
  }

  private String compilePredicate(
      Predicate p,
      Map<String, DatasetDescriptor> datasets,
      Map<String, String> aliases,
      List<Object> params) {
    Field f = resolveField(p.field(), datasets, aliases);
    String col = f.alias() + "." + f.column();
    FilterOp op = p.op();
    JsonNode value = p.value();
    switch (op) {
      case IS_NULL -> {
        return col + " IS NULL";
      }
      case BETWEEN -> {
        if (value == null || !value.isArray() || value.size() != 2) {
          throw new QueryException(
              QueryErrorCode.TYPE_MISMATCH, "BETWEEN needs two values", Map.of("field", p.field()));
        }
        params.add(coerce(f, value.get(0)));
        params.add(coerce(f, value.get(1)));
        return col + " BETWEEN ? AND ?";
      }
      case IN -> {
        if (value == null || !value.isArray() || value.isEmpty()) {
          throw new QueryException(
              QueryErrorCode.TYPE_MISMATCH,
              "IN needs a non-empty list",
              Map.of("field", p.field()));
        }
        List<String> placeholders = new ArrayList<>();
        for (JsonNode v : value) {
          params.add(coerce(f, v));
          placeholders.add("?");
        }
        return col + " IN (" + String.join(", ", placeholders) + ")";
      }
      default -> {
        if (value == null || value.isNull()) {
          throw new QueryException(
              QueryErrorCode.TYPE_MISMATCH, op + " needs a value", Map.of("field", p.field()));
        }
        params.add(coerce(f, value));
        return col + " " + sqlOp(op) + " ?";
      }
    }
  }

  private static String sqlOp(FilterOp op) {
    return switch (op) {
      case EQ -> "=";
      case NE -> "<>";
      case GT -> ">";
      case GTE -> ">=";
      case LT -> "<";
      case LTE -> "<=";
      default -> throw new QueryException(QueryErrorCode.INVALID_PLAN, "unsupported op " + op);
    };
  }

  /** Coerces a JSON value to the typed Java object the field's column expects. */
  private static Object coerce(Field f, JsonNode value) {
    try {
      return switch (f.type()) {
        case DATE -> LocalDate.parse(value.asText());
        case DECIMAL -> new BigDecimal(value.asText());
        case TEXT -> value.asText();
        case BOOLEAN -> value.asBoolean();
        case TIMESTAMP -> Instant.parse(value.asText());
        case UUID -> UUID.fromString(value.asText());
        case INTEGER -> value.asInt();
      };
    } catch (RuntimeException e) {
      throw new QueryException(
          QueryErrorCode.TYPE_MISMATCH,
          "value '" + value.asText() + "' is not a valid " + f.type(),
          Map.of("field", f.column()));
    }
  }

  private Field resolveField(
      String name, Map<String, DatasetDescriptor> datasets, Map<String, String> aliases) {
    if (name.contains(".")) {
      int dot = name.indexOf('.');
      String ds = name.substring(0, dot);
      String field = name.substring(dot + 1);
      DatasetDescriptor d = datasets.get(ds);
      if (d == null) {
        throw new QueryException(
            QueryErrorCode.UNKNOWN_DATASET, "Unknown dataset: " + ds, Map.of("identifier", ds));
      }
      FieldDescriptor fd = d.field(field);
      if (fd == null) {
        throw new QueryException(
            QueryErrorCode.UNKNOWN_FIELD, "Unknown field: " + name, Map.of("field", name));
      }
      return new Field(ds, aliases.get(ds), fd.column(), fd.type(), fd.unit());
    }
    Field found = null;
    for (Map.Entry<String, DatasetDescriptor> e : datasets.entrySet()) {
      FieldDescriptor fd = e.getValue().field(name);
      if (fd != null) {
        if (found != null) {
          throw new QueryException(
              QueryErrorCode.UNKNOWN_FIELD,
              "ambiguous field '" + name + "'; qualify it as <dataset>.<field>",
              Map.of("field", name));
        }
        found = new Field(e.getKey(), aliases.get(e.getKey()), fd.column(), fd.type(), fd.unit());
      }
    }
    if (found == null) {
      throw new QueryException(
          QueryErrorCode.UNKNOWN_FIELD, "Unknown field: " + name, Map.of("field", name));
    }
    return found;
  }

  private FieldDescriptor descriptorOf(Map<String, DatasetDescriptor> datasets, String name) {
    if (name.contains(".")) {
      String ds = name.substring(0, name.indexOf('.'));
      String field = name.substring(name.indexOf('.') + 1);
      DatasetDescriptor d = datasets.get(ds);
      return d == null ? null : d.field(field);
    }
    FieldDescriptor found = null;
    for (DatasetDescriptor d : datasets.values()) {
      FieldDescriptor fd = d.field(name);
      if (fd != null) {
        if (found != null) {
          return null;
        }
        found = fd;
      }
    }
    return found;
  }

  private void authorize(
      UserRole role,
      DatasetDescriptor primary,
      Map<String, DatasetDescriptor> datasets,
      List<RelationshipDescriptor> joins,
      List<SelectItem> select) {
    require(role, primary.permission());
    for (DatasetDescriptor d : datasets.values()) {
      require(role, d.permission());
    }
    for (RelationshipDescriptor rel : joins) {
      require(role, rel.permission());
    }
    for (SelectItem item : select) {
      FieldDescriptor fd = descriptorOf(datasets, item.field());
      if (fd != null && fd.permission() != null) {
        require(role, fd.permission());
      }
    }
  }

  private void require(UserRole role, String resource) {
    if (resource == null) {
      return;
    }
    permissions.require(role, new ResourceKey(resource), PermissionAction.READ);
  }
}
