---
name: add-reporting-tool
description: Add or change an Ask Goldy's reporting tool (the fixed, enum-validated tools the AI assistant may call). Use when the assistant should be able to answer a new kind of question, or an existing tool needs new parameters.
---

The contract is `docs/contracts/ai-tool-boundary.md`. The non-negotiable part: tool
parameters are bounded and enum-validated. No parameter may accept SQL, a free-text
field name, or a free-text filter expression.

## First, try not to add a tool

`GET_METRIC`, `COMPARE_METRIC_PERIODS` and `RANK_DIMENSION` already answer any question
about a catalogue metric. If the question is "metric X over period Y", add the metric
(`add-metric` skill) and the existing tools pick it up. Add a dedicated tool only for a
composite answer (several metrics in one table, a status summary).

## Steps

1. Add a value to `reporting/ToolId`.
2. Add an input record `<Name>Input implements ToolInput` in `reporting/`. Fields are
   enums, `LocalDate`, `MetricId`, bounded ints, validated in the compact constructor.
   Spring AI derives the JSON schema the model sees from this record, so field names
   and types are the model's whole API.
3. Add `<Name>Tool implements ReportingTool` as a `@Component`:
   - `name()` is the snake_case name the model calls; `description()` says when to use
     it and when not to (the model chooses tools from this text);
   - `resource()` is the permission resource; `ToolDispatcher` enforces it, plus each
     catalogue metric's own permission;
   - `execute` reads through `MetricQueryService` / semantic queries only and returns
     widget specs via `WidgetRenderer`;
   - implement `toMetricQueries` so the answer can be saved as a dashboard widget.
   `ToolRegistry` picks up every `ReportingTool` bean; nothing else to register.
4. Add eval cases to `backend/src/test/resources/conversational-eval/questions.json`
   (question, role, `expectedTools`), including one where the tool should *not* be
   chosen.
5. Add the tool to the table in `docs/contracts/ai-tool-boundary.md`.

## Tests

A `<Name>ToolTest` (copy `GetReservationSummaryToolTest`), plus coverage in
`ToolDispatcherTest` for the permission denial, and `ToolToMetricQueriesTest` if it
implements `toMetricQueries`. `reporting` may depend only on `semantic` and `auth`;
`ArchitectureBoundariesTest` will fail otherwise.
