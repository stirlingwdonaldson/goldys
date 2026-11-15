package com.goldys.platform.workspace;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.conversational.CreateDashboardDraftTool;
import com.goldys.platform.conversational.DashboardDraft;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolRegistry;
import com.goldys.platform.reporting.ToolResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Worker-supplied roles, SQL and permission assertions are never accepted. */
@Service
public class WorkspaceReporting {
  private final WorkspaceJobs jobs;
  private final ToolRegistry registry;
  private final ToolDispatcher dispatcher;
  private final ObjectMapper json;

  public WorkspaceReporting(
      WorkspaceJobs jobs, ToolRegistry registry, ToolDispatcher dispatcher, ObjectMapper json) {
    this.jobs = jobs;
    this.registry = registry;
    this.dispatcher = dispatcher;
    this.json = json;
  }

  public record ToolSpec(String id, String name, String description, JsonNode parameters) {}

  @Transactional
  public java.util.List<ToolSpec> tools(UUID job, UUID fence) {
    jobs.authorizedRole(job, fence);
    var config =
        new com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder(
                com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_7,
                com.github.victools.jsonschema.generator.OptionPreset.PLAIN_JSON)
            .with(
                new com.github.victools.jsonschema.module.jackson.JacksonModule(
                    com.github.victools.jsonschema.module.jackson.JacksonOption
                        .FLATTENED_ENUMS_FROM_JSONVALUE));
    config
        .forTypesInGeneral()
        .withCustomDefinitionProvider(
            (scope, context) -> {
              if (scope.getErasedType() == java.time.LocalDate.class) {
                return new com.github.victools.jsonschema.generator.CustomDefinition(
                    json.createObjectNode().put("type", "string").put("format", "date"));
              }
              return null;
            });
    var generator = new com.github.victools.jsonschema.generator.SchemaGenerator(config.build());
    return java.util.Arrays.stream(ToolId.values())
        .map(registry::find)
        .flatMap(java.util.Optional::stream)
        .map(
            tool ->
                new ToolSpec(
                    tool.id().name(),
                    "goldys_" + tool.name(),
                    tool.description(),
                    generator.generateSchema(tool.inputType())))
        .toList();
  }

  public record Result(@JsonUnwrapped ToolResult reporting, DashboardDraft draft) {}

  @Transactional
  public Result execute(UUID job, UUID fence, ToolId id, JsonNode arguments) {
    var role = jobs.authorizedRole(job, fence);
    var tool =
        registry.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown reporting tool"));
    if (arguments == null || !arguments.isObject() || arguments.toString().length() > 64000) {
      throw new IllegalArgumentException("Invalid reporting arguments");
    }
    com.goldys.platform.reporting.ToolInput input;
    try {
      input =
          json.readerFor(tool.inputType())
              .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
              .readValue(arguments);
    } catch (java.io.IOException invalid) {
      throw new IllegalArgumentException("Invalid reporting arguments");
    }
    var result = dispatcher.dispatch(id, input, role);
    // Recheck lease/deadline after the query; an expired worker receives no data.
    jobs.authorizedRole(job, fence);
    DashboardDraft draft =
        tool instanceof CreateDashboardDraftTool draftTool ? draftTool.toDraft(input) : null;
    return new Result(result, draft);
  }
}
