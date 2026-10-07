package com.goldys.platform.conversational.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.conversational.AnswerPayload;
import com.goldys.platform.conversational.AssistantService;
import com.goldys.platform.conversational.ConversationEvent;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

/**
 * Tier 2 (gated live-LLM) eval for "Ask Goldy's". Runs the real model against the ten-category PRD
 * question set and scores three non-deterministic dimensions that the deterministic Tier 1 harness
 * cannot measure:
 *
 * <ol>
 *   <li><b>tool selection</b> — did the answer's tool trace contain the expected tool(s) for the
 *       question's data need;
 *   <li><b>unsupported-claim rate</b> — does the answer text assert a number the tools did not
 *       return (a heuristic: claim-shaped numbers in the prose vs. numbers in the widgets/notices);
 *   <li><b>causal-language detection</b> — for correlational questions, does the answer use
 *       unhedged causal phrasing ({@code because}/{@code fell}/{@code drove}) rather than hedging
 *       ({@code accounts for}/{@code is consistent with}).
 * </ol>
 *
 * <p>The suite is <b>off by default</b> and skipped in CI: it is disabled unless {@code
 * CONVERSATIONAL_EVAL_ENABLED=true}, and then aborted (not failed) when no {@link ChatModel} bean
 * is present — which is the default {@code spring.ai.model.chat=none} context. To actually run it,
 * set {@code CONVERSATIONAL_EVAL_ENABLED=true}, {@code SPRING_AI_MODEL_CHAT=openai}, and a real
 * {@code OPENAI_API_KEY} (no key is read from this repo). Model-quality scores are written to
 * {@code build/reports/conversational-eval.md} and reported, never asserted, since a live model is
 * non-deterministic; only harness mechanics (all ten categories ran, the report was produced) are
 * asserted.
 */
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "CONVERSATIONAL_EVAL_ENABLED", matches = "true")
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class LiveLlmEvalTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final UserRole DENIED =
      new UserRole(new DepartmentCode("FOH"), new SeniorityCode("STAFF"));

  /** A number shaped like a claim: currency, thousands-separated, decimal, or percentage. */
  private static final Pattern CLAIM_NUMBER = Pattern.compile("\\$?\\d[\\d,]*(?:\\.\\d+)?%?");

  private static final List<String> UNHEDGED_CAUSAL =
      List.of("because", "fell", "drove", "led to", "caused", "due to");
  private static final List<String> HEDGED =
      List.of(
          "accounts for", "is consistent with", "correlat", "may", "could", "might", "suggests");

  @Autowired ObjectProvider<ChatModel> chatModel;
  @Autowired ObjectProvider<AssistantService> assistantService;
  @Autowired ObjectMapper mapper;

  @Test
  void scoresTheTenCategoryQuestionSet() throws IOException {
    ChatModel model = chatModel.getIfAvailable();
    AssistantService assistant = assistantService.getIfAvailable();
    Assumptions.assumeTrue(
        model != null,
        "No ChatModel bean (spring.ai.model.chat=none); live-LLM eval skipped. "
            + "Set SPRING_AI_MODEL_CHAT=openai to run it.");
    Assumptions.assumeTrue(
        assistant != null,
        "No AssistantService bean; live-LLM eval skipped (no model configured).");

    List<Question> questions = loadQuestions();
    List<ScoredAnswer> scored = new ArrayList<>();
    for (Question question : questions) {
      scored.add(runAndScore(question, assistant));
    }

    String report = renderReport(scored);
    Path reportPath = Path.of("build", "reports", "conversational-eval.md");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, report);

    // Harness mechanics only — never the model's quality, which is non-deterministic.
    assertThat(scored).hasSize(10);
    assertThat(Files.isRegularFile(reportPath)).isTrue();
  }

  private ScoredAnswer runAndScore(Question question, AssistantService assistant) {
    UserRole role = "denied".equals(question.role()) ? DENIED : OWNER;
    List<ConversationEvent> events =
        assistant.stream(List.of(new UserMessage(question.question())), role).collectList().block();

    StringBuilder text = new StringBuilder();
    AnswerPayload payload = null;
    for (ConversationEvent event : events) {
      if (event instanceof ConversationEvent.TextDelta delta) {
        text.append(delta.delta());
      } else if (event instanceof ConversationEvent.Answer answer) {
        payload = answer.payload();
      }
    }
    String answerText = text.toString().trim();

    List<String> tools =
        payload == null
            ? List.of()
            : payload.trace().stream().map(AnswerPayload.TraceEntry::tool).toList();
    boolean draft = payload != null && payload.draft() != null;

    ToolSelection selection = scoreToolSelection(question, tools, draft, answerText);
    Set<String> toolNumbers = toolNumbers(payload);
    List<String> claims = claimNumbers(answerText);
    List<String> unsupported =
        claims.stream()
            .filter(token -> canonical(token) != null && !toolNumbers.contains(canonical(token)))
            .toList();

    boolean causalFlagged = false;
    boolean causalHedged = false;
    if (question.causal()) {
      String lower = answerText.toLowerCase(Locale.ROOT);
      causalFlagged = containsAny(lower, UNHEDGED_CAUSAL);
      causalHedged = containsAny(lower, HEDGED);
    }

    return new ScoredAnswer(
        question,
        answerText,
        tools,
        draft,
        selection,
        claims.size(),
        unsupported,
        causalFlagged,
        causalHedged);
  }

  /**
   * Scores whether the model picked the right tool(s). The trace records only <em>successful</em>
   * calls: a denied call surfaces as an error to the model, not a trace entry, so the permission
   * question is scored on whether the denial was relayed; the dashboard draft is carried on {@link
   * AnswerPayload#draft()} rather than the trace.
   */
  private ToolSelection scoreToolSelection(
      Question question, List<String> tools, boolean draft, String answerText) {
    Set<String> observed = new LinkedHashSet<>(tools);
    if (draft) {
      observed.add("create_dashboard_draft");
    }

    if ("denied".equals(question.role())) {
      boolean relayed =
          containsAny(
              answerText.toLowerCase(Locale.ROOT),
              List.of("access", "permission", "authorized", "denied"));
      return new ToolSelection(
          relayed, "denial relayed in the answer", question.expectedTools(), observed);
    }

    List<String> expected = question.expectedTools();
    if (expected.isEmpty()) {
      boolean calledNoTool = observed.isEmpty();
      return new ToolSelection(
          calledNoTool,
          calledNoTool ? "called no tool (as expected)" : "called a tool unexpectedly",
          expected,
          observed);
    }

    boolean allPresent = observed.containsAll(expected);
    return new ToolSelection(
        allPresent,
        allPresent ? "all expected tools observed" : "missing expected tool(s)",
        expected,
        observed);
  }

  private Set<String> toolNumbers(AnswerPayload payload) {
    Set<String> numbers = new LinkedHashSet<>();
    if (payload == null) {
      return numbers;
    }
    try {
      collectNumbers(mapper.writeValueAsString(payload.widgets()), numbers);
    } catch (IOException ignored) {
      // Widget serialization is best-effort; notices still contribute numbers below.
    }
    for (String notice : payload.notices()) {
      collectNumbers(notice, numbers);
    }
    return numbers;
  }

  private static void collectNumbers(String text, Set<String> into) {
    if (text == null) {
      return;
    }
    Matcher matcher = CLAIM_NUMBER.matcher(text);
    while (matcher.find()) {
      String token = matcher.group();
      if (!isClaim(token)) {
        continue;
      }
      String value = canonical(token);
      if (value != null) {
        into.add(value);
      }
    }
  }

  private static List<String> claimNumbers(String text) {
    List<String> claims = new ArrayList<>();
    if (text == null) {
      return claims;
    }
    Matcher matcher = CLAIM_NUMBER.matcher(text);
    while (matcher.find()) {
      String token = matcher.group();
      if (isClaim(token)) {
        claims.add(token);
      }
    }
    return claims;
  }

  /** A claim is a number with a decimal, a thousands separator, a currency sign, or a % sign. */
  private static boolean isClaim(String token) {
    return token.indexOf('.') >= 0
        || token.indexOf(',') >= 0
        || token.indexOf('%') >= 0
        || token.startsWith("$");
  }

  /**
   * Normalizes a claim token to a scale-insensitive numeric form ("100.0%" and "100%" both →
   * "100").
   */
  private static String canonical(String token) {
    try {
      return new BigDecimal(token.replace("$", "").replace(",", "").replace("%", ""))
          .stripTrailingZeros()
          .toPlainString();
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static boolean containsAny(String text, List<String> markers) {
    for (String marker : markers) {
      if (text.contains(marker)) {
        return true;
      }
    }
    return false;
  }

  private List<Question> loadQuestions() throws IOException {
    JsonNode root =
        mapper.readTree(
            new ClassPathResource("conversational-eval/questions.json").getInputStream());
    List<Question> questions = new ArrayList<>();
    for (JsonNode node : root) {
      List<String> expectedTools = new ArrayList<>();
      node.path("expectedTools").forEach(tool -> expectedTools.add(tool.asText()));
      questions.add(
          new Question(
              node.path("category").asText(),
              node.path("question").asText(),
              node.path("role").asText(),
              expectedTools,
              node.path("causal").asBoolean(false)));
    }
    return questions;
  }

  private String renderReport(List<ScoredAnswer> scored) {
    long toolSelectionPassed = scored.stream().filter(s -> s.toolSelection().passed()).count();
    long claimNumbers = scored.stream().mapToLong(ScoredAnswer::claimNumbers).sum();
    long unsupported = scored.stream().mapToLong(s -> s.unsupported().size()).sum();
    long causalFlagged = scored.stream().filter(ScoredAnswer::causalFlagged).count();
    long causalQuestions = scored.stream().filter(s -> s.question().causal()).count();

    StringBuilder report = new StringBuilder();
    report.append("# Ask Goldy's — Live-LLM Eval (Tier 2)\n\n");
    report.append("- **Run:** ").append(Instant.now()).append('\n');
    report.append("- **Questions:** ").append(scored.size()).append(" (ten PRD categories)\n");
    report
        .append("- **Tool selection:** ")
        .append(toolSelectionPassed)
        .append('/')
        .append(scored.size())
        .append('\n');
    report
        .append("- **Unsupported claims:** ")
        .append(unsupported)
        .append(" of ")
        .append(claimNumbers)
        .append(" claim-shaped numbers\n");
    report
        .append("- **Causal language:** unhedged on ")
        .append(causalFlagged)
        .append('/')
        .append(causalQuestions)
        .append(" correlational question(s)\n\n");

    report.append("## Per-question results\n\n");
    report.append("| Category | Tool selection | Unsupported | Causal |\n");
    report.append("|---|---|---|---|\n");
    for (ScoredAnswer s : scored) {
      String causal =
          s.question().causal()
              ? (s.causalFlagged() ? "unhedged" : (s.causalHedged() ? "hedged" : "none"))
              : "—";
      report
          .append("| ")
          .append(s.question().category())
          .append(" | ")
          .append(s.toolSelection().passed() ? "✓ " : "✗ ")
          .append(s.toolSelection().note())
          .append(" | ")
          .append(s.unsupported().size())
          .append(" | ")
          .append(causal)
          .append(" |\n");
    }
    report.append('\n');

    report.append("## Method\n\n");
    report.append(
        "- **tool selection** — expected tool names vs. `answer.trace` tool names; "
            + "`create_dashboard_draft` is detected via `answer.draft`, and the denied question "
            + "is scored on whether the answer relays the access denial (a denied call records no "
            + "trace entry).\n");
    report.append(
        "- **unsupported claim** — claim-shaped numbers (decimal / thousands-separated / currency / "
            + "percent) in the answer prose that do not appear among the numbers the tools returned "
            + "(widgets + notices). Computed deltas a tool returns in its notices count as supported; "
            + "a delta the model invents from nothing does not.\n");
    report.append(
        "- **causal language** — unhedged markers (`because`, `fell`, `drove`, `led to`, `caused`, "
            + "`due to`) vs. hedged markers (`accounts for`, `is consistent with`, `correlat`, "
            + "`may`, `could`, `might`, `suggests`), matched case-insensitively on correlational "
            + "questions.\n\n");

    report.append("## Answers\n\n");
    for (ScoredAnswer s : scored) {
      report.append("### ").append(s.question().category()).append('\n');
      report.append("- **question:** ").append(s.question().question()).append('\n');
      report
          .append("- **tools:** ")
          .append(s.tools().isEmpty() ? "(none)" : String.join(", ", s.tools()))
          .append(s.draft() ? " (+ draft)" : "")
          .append('\n');
      report
          .append("- **tool selection:** ")
          .append(s.toolSelection().passed() ? "pass — " : "fail — ")
          .append(s.toolSelection().note())
          .append(" (expected ")
          .append(
              s.toolSelection().expected().isEmpty()
                  ? "none"
                  : String.join(", ", s.toolSelection().expected()))
          .append("; observed ")
          .append(
              s.toolSelection().observed().isEmpty()
                  ? "none"
                  : String.join(", ", s.toolSelection().observed()))
          .append(")\n");
      report
          .append("- **answer:** ")
          .append(s.answerText().isBlank() ? "(empty)" : s.answerText())
          .append("\n\n");
    }

    return report.toString();
  }

  private record Question(
      String category, String question, String role, List<String> expectedTools, boolean causal) {}

  private record ToolSelection(
      boolean passed, String note, List<String> expected, Set<String> observed) {}

  private record ScoredAnswer(
      Question question,
      String answerText,
      List<String> tools,
      boolean draft,
      ToolSelection toolSelection,
      int claimNumbers,
      List<String> unsupported,
      boolean causalFlagged,
      boolean causalHedged) {}
}
