package com.goldys.platform.conversational.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolInput;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.widget.WidgetSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Tier 1 (deterministic) eval harness for "Ask Goldy's".
 *
 * <p>This half of the evaluation set replays <em>golden</em> tool-call sequences through the real
 * {@link ToolDispatcher} — the same authorize + execute boundary the live assistant uses — against
 * seeded, resolved data. It asserts the four deterministic scoring dimensions: numeric correctness,
 * tool selection (result shapes), permission correctness, and widget-schema validity.
 *
 * <p>The fifth dimension, unsupported-claim rate, is <em>not</em> measured here: the {@link
 * ScriptedAgent} is a deterministic stand-in that makes no claims of its own, so it can neither
 * hallucinate a number nor leak one that is not in the tool trace. Claim-scoring is a Tier 2 (gated
 * live-LLM) concern.
 *
 * <p>No live model, no network calls, no SQL or arbitrary expressions anywhere in the golden path.
 */
public final class ConversationalEvalHarness {

  private ConversationalEvalHarness() {}

  /** One golden tool call the reference agent is expected to issue for a scenario. */
  public record ToolCall(ToolId tool, ToolInput input) {
    public ToolCall {
      if (tool == null) {
        throw new IllegalArgumentException("tool must not be null");
      }
      if (input == null) {
        throw new IllegalArgumentException("input must not be null");
      }
    }
  }

  /**
   * A scenario: a natural-language question plus the golden tool-call sequence a correct agent
   * would produce. The {@code category} is the PRD evaluation-set category (simple retrieval,
   * period comparison, cross-domain reasoning, ambiguous clarification, missing data, permission
   * denial, unresolved conflict, dashboard generation, prompt injection, arbitrary SQL).
   */
  public record Scenario(
      String name, String category, String question, UserRole role, List<ToolCall> goldenCalls) {
    public Scenario {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("name must not be blank");
      }
      if (category == null || category.isBlank()) {
        throw new IllegalArgumentException("category must not be blank");
      }
      if (question == null || question.isBlank()) {
        throw new IllegalArgumentException("question must not be blank");
      }
      if (role == null) {
        throw new IllegalArgumentException("role must not be null");
      }
      goldenCalls = goldenCalls == null ? List.of() : List.copyOf(goldenCalls);
    }
  }

  /**
   * The outcome of a single replayed tool call: the widget result, or the failure that surfaced.
   */
  public record Step(ToolCall call, ToolResult result, RuntimeException failure) {
    public Step {
      if (call == null) {
        throw new IllegalArgumentException("call must not be null");
      }
    }

    /** True when the dispatcher returned a widget rather than throwing. */
    public boolean succeeded() {
      return failure == null;
    }
  }

  /**
   * A deterministic stand-in for the model agent. It issues a scenario's golden calls, in order,
   * through the real dispatcher and records each outcome. It never invents or filters a number — a
   * denied call surfaces its denial, a missing/conflicted value surfaces its notice, and a call
   * that throws records its exception.
   */
  public static final class ScriptedAgent {
    private final ToolDispatcher dispatcher;

    public ScriptedAgent(ToolDispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    public List<Step> answer(Scenario scenario) {
      return scenario.goldenCalls().stream()
          .map(
              call -> {
                try {
                  return new Step(
                      call, dispatcher.dispatch(call.tool(), call.input(), scenario.role()), null);
                } catch (RuntimeException e) {
                  return new Step(call, null, e);
                }
              })
          .toList();
    }
  }

  /**
   * Validates a serialized widget against the structural rules of {@code
   * docs/contracts/widget-spec.schema.json}: the versioned discriminator, the variant-specific
   * required fields, and the constrained value formats. There is no JSON-Schema validator on the
   * test classpath, so this checks the schema's load-bearing constraints directly (mirroring the
   * approach of {@code WidgetSchemaTest} / {@code WidgetSpecTest}).
   */
  public static final class WidgetSchemaValidator {
    /** The five discriminated widget variants, in schema {@code oneOf} order. */
    private static final Set<String> ALLOWED_TYPES =
        Set.of("stat", "time-series", "bar-chart", "table", "ranked-list");

    /** The constrained value formats shared by {@code format} and {@code yFormat}. */
    private static final Set<String> ALLOWED_FORMATS =
        Set.of("currency", "number", "percent", "text");

    private final ObjectMapper mapper;
    private final int schemaVersion;

    public WidgetSchemaValidator(ObjectMapper mapper) {
      this.mapper = mapper;
      this.schemaVersion =
          loadSchema(mapper).at("/$defs/base/properties/schemaVersion/const").asInt();
    }

    public void assertValid(WidgetSpec widget) {
      JsonNode node = mapper.valueToTree(widget);

      assertThat(node.path("schemaVersion").asInt())
          .as("widget schemaVersion must match the contract")
          .isEqualTo(schemaVersion);

      String type = node.path("type").asText();
      assertThat(type).as("widget must declare one of the discriminated types").isIn(ALLOWED_TYPES);

      String id = node.path("id").asText();
      String title = node.path("title").asText();
      assertThat(id).as("widget id").isNotBlank();
      assertThat(title).as("widget title").isNotBlank();
      assertThat(title.length()).as("widget title must respect maxLength").isLessThanOrEqualTo(120);

      switch (type) {
        case "stat" -> assertFormat(node, "format");
        case "time-series", "bar-chart" -> {
          assertThat(node.has("series")).as("time-series/bar-chart must carry series").isTrue();
          node.path("series")
              .forEach(
                  s -> {
                    assertThat(s.path("key").asText()).as("series key").isNotBlank();
                    assertThat(s.path("label").asText()).as("series label").isNotBlank();
                    assertThat(s.has("points")).as("series points").isTrue();
                    s.path("points").forEach(p -> assertThat(p.has("x")).as("point x").isTrue());
                  });
          assertFormat(node, "yFormat");
        }
        case "table" -> {
          assertThat(node.has("columns")).as("table must carry columns").isTrue();
          assertThat(node.has("rows")).as("table must carry rows").isTrue();
          node.path("columns")
              .forEach(
                  c -> {
                    assertThat(c.path("key").asText()).as("column key").isNotBlank();
                    assertThat(c.path("label").asText()).as("column label").isNotBlank();
                  });
        }
        case "ranked-list" -> {
          assertThat(node.has("items")).as("ranked-list must carry items").isTrue();
          node.path("items")
              .forEach(i -> assertThat(i.path("label").asText()).as("item label").isNotBlank());
        }
        default -> throw new AssertionError("unhandled widget type: " + type);
      }
    }

    private void assertFormat(JsonNode node, String field) {
      if (node.has(field) && !node.get(field).isNull()) {
        assertThat(node.get(field).asText())
            .as("widget %s must be a constrained format", field)
            .isIn(ALLOWED_FORMATS);
      }
    }

    private static JsonNode loadSchema(ObjectMapper mapper) {
      for (Path candidate :
          List.of(
              Path.of("..", "docs", "contracts", "widget-spec.schema.json"),
              Path.of("docs", "contracts", "widget-spec.schema.json"))) {
        if (Files.isRegularFile(candidate)) {
          try {
            return mapper.readTree(Files.readString(candidate));
          } catch (Exception e) {
            throw new IllegalStateException("Could not read widget schema: " + candidate, e);
          }
        }
      }
      throw new IllegalStateException(
          "widget-spec.schema.json not found; run the test from backend/ or the repo root");
    }
  }
}
