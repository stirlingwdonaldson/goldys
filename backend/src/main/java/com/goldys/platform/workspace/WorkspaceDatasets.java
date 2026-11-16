package com.goldys.platform.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.reporting.*;
import com.goldys.platform.semantic.catalog.*;
import com.goldys.platform.widget.TableWidgetSpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One reviewed dataset: existing daily governed sales metrics, never a generic query/export. */
@Service
public class WorkspaceDatasets {
  private final WorkspaceReporting reporting;
  private final WorkspaceFiles files;
  private final ObjectMapper json;

  public WorkspaceDatasets(WorkspaceReporting reporting, WorkspaceFiles files, ObjectMapper json) {
    this.reporting = reporting;
    this.files = files;
    this.json = json;
  }

  public record Dataset(
      WorkspaceFiles.FileInfo file,
      UUID reportingEvidenceId,
      List<String> notices,
      List<MetricProvenance> provenance) {}

  @Transactional
  public Dataset sales(UUID job, UUID fence, LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30)
      throw new IllegalArgumentException("Sales dataset range must contain 1–31 days");
    var plan =
        new RunMetricAnalysisInput(
            List.of(
                new GetMetricInput(MetricId.SALES_NET, from, to, TimeGrain.DAY, Set.of()),
                new GetMetricInput(MetricId.SALES_GROSS, from, to, TimeGrain.DAY, Set.of()),
                new GetMetricInput(MetricId.SALES_GST, from, to, TimeGrain.DAY, Set.of())));
    var result = reporting.execute(job, fence, ToolId.RUN_METRIC_ANALYSIS, json.valueToTree(plan));
    var table = (TableWidgetSpec) result.reporting().widget();
    if (table.rows().size() > 93)
      throw new IllegalStateException("Sales dataset exceeds reviewed shape");
    var csv = new StringBuilder("date,metric,value,unit,status\n");
    for (var row : table.rows()) {
      csv.append(cell(row.get("bucket")))
          .append(',')
          .append(cell(row.get("metric")))
          .append(',')
          .append(cell(row.get("value")))
          .append(',')
          .append(cell(row.get("unit")))
          .append(',')
          .append(cell(row.get("value") == null ? "MISSING" : row.get("status")))
          .append('\n');
    }
    var file =
        files.artifact(
            job,
            fence,
            "governed-sales-" + from + "-" + to + ".csv",
            csv.toString().getBytes(StandardCharsets.UTF_8));
    return new Dataset(
        file, result.evidenceId(), result.reporting().notices(), result.reporting().provenance());
  }

  private static String cell(Object value) {
    if (value == null) return "";
    String text = value.toString();
    if (text.startsWith("=") || text.startsWith("+") || text.startsWith("@"))
      throw new IllegalArgumentException("Unsafe dataset cell");
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
