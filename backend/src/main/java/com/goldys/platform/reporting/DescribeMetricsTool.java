package com.goldys.platform.reporting;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricDefinition;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Discovers business meaning directly from the executable catalogue, never a second ontology. */
@Component
public class DescribeMetricsTool implements ReportingTool {
  private final MetricCatalog catalog;
  private final PermissionService permissions;

  public DescribeMetricsTool(MetricCatalog catalog, PermissionService permissions) {
    this.catalog = catalog;
    this.permissions = permissions;
  }

  @Override
  public ToolId id() {
    return ToolId.DESCRIBE_METRICS;
  }

  @Override
  public String name() {
    return "describe_metrics";
  }

  @Override
  public String description() {
    return "Discover approved metric definitions, units, versions, dimensions and grains before planning "
        + "an unfamiliar analysis. Empty metrics lists accessible definitions; explicit metrics require "
        + "access to every operand. Definitions do not establish data availability. Never invent formulas.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return DescribeMetricsInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("conversational.chat");
  }

  @Override
  public Set<MetricId> referencedMetrics(ToolInput input) {
    return require(input).metrics();
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    Set<MetricId> requested = require(input).metrics();
    List<MetricId> selected =
        (requested.isEmpty() ? catalog.ids() : requested)
            .stream().filter(id -> !requested.isEmpty() || canRead(role, id)).sorted().toList();
    List<Map<String, Object>> rows =
        selected.stream().map(id -> row(catalog.definition(id))).toList();
    List<Column> columns =
        List.of(
                "metric",
                "name",
                "definition",
                "formula",
                "unit",
                "version",
                "dimensions",
                "grains",
                "notes")
            .stream()
            .map(key -> new Column(key, key, null))
            .toList();
    return new ToolResult(
        new TableWidgetSpec(
            UUID.randomUUID().toString(),
            "Approved metrics",
            "Definitions describe meaning, not source completeness or availability",
            columns,
            rows,
            null),
        List.of(
            "Use returned definitions unchanged; unsupported concepts require review, not a substitute formula."),
        List.of(),
        List.of());
  }

  private boolean canRead(UserRole role, MetricId id) {
    return catalog.requiredPermissions(id).stream()
        .allMatch(
            resource ->
                permissions.isAllowed(role, new ResourceKey(resource), PermissionAction.READ));
  }

  private static Map<String, Object> row(MetricDefinition definition) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("metric", definition.id().value());
    row.put("name", definition.name());
    row.put("definition", definition.definition());
    row.put("formula", definition.formula());
    row.put("unit", definition.unit());
    row.put("version", definition.version());
    row.put("dimensions", definition.validDimensions().stream().sorted().map(Enum::name).toList());
    row.put("grains", definition.allowedGrains().stream().sorted().map(Enum::name).toList());
    row.put("notes", definition.notes());
    return row;
  }

  private static DescribeMetricsInput require(ToolInput input) {
    if (!(input instanceof DescribeMetricsInput in))
      throw new IllegalArgumentException("Expected DescribeMetricsInput");
    return in;
  }
}
