package com.goldys.platform.connectors.ctb.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.connectors.ctb.LlmInvoicePdfExtractor;
import com.goldys.platform.connectors.ctb.PdfExtractedInvoice;
import com.goldys.platform.connectors.ctb.PdfExtractedLine;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

/**
 * Live-LLM eval for {@link LlmInvoicePdfExtractor}: runs the real model over fixture PDF texts and
 * asserts the extracted lines match the expected enrichment fields exactly. Because a hallucinated
 * UOM / pack size / WET silently corrupts unit-cost and liquor-tax accounting, this eval is strict
 * — a mismatch is a failure, not a score.
 *
 * <p>Off by default and skipped in CI: enabled only with {@code INVOICE_PDF_EVAL_ENABLED=true},
 * then aborted (not failed) when no {@link ChatModel} bean is present (the default {@code
 * spring.ai.model.chat=none} context). To run: {@code INVOICE_PDF_EVAL_ENABLED=true
 * SPRING_AI_MODEL_CHAT=openai OPENAI_API_KEY=… ./gradlew test --tests
 * com.goldys.platform.connectors.ctb.eval.LlmInvoicePdfExtractorEvalTest}.
 */
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "INVOICE_PDF_EVAL_ENABLED", matches = "true")
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class LlmInvoicePdfExtractorEvalTest {

  @Autowired ObjectProvider<ChatModel> chatModel;
  @Autowired ObjectMapper mapper;

  @Test
  void extractsTheFixtureLinesExactly() throws IOException {
    ChatModel model = chatModel.getIfAvailable();
    Assumptions.assumeTrue(
        model != null,
        "No ChatModel bean (spring.ai.model.chat=none); invoice-PDF LLM eval skipped. "
            + "Set SPRING_AI_MODEL_CHAT=openai to run it.");
    LlmInvoicePdfExtractor extractor = new LlmInvoicePdfExtractor(chatModel, mapper);

    List<Fixture> fixtures = loadFixtures();
    List<Result> results = new ArrayList<>();
    for (Fixture fixture : fixtures) {
      PdfExtractedInvoice out = extractor.extract(fixture.text());
      results.add(new Result(fixture, out, diff(fixture, out)));
    }

    String report = renderReport(results);
    Path reportPath = Path.of("build", "reports", "invoice-pdf-eval.md");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, report);

    // Strict: every fixture's lines must match the expected enrichment fields exactly.
    assertThat(results)
        .allSatisfy(r -> assertThat(r.diff()).as("fixture %s", r.fixture().name()).isEmpty());
  }

  /** Field-by-field mismatches; empty when the extraction is exact. */
  private static List<String> diff(Fixture fixture, PdfExtractedInvoice out) {
    List<String> diffs = new ArrayList<>();
    if (!java.util.Objects.equals(fixture.invoiceNumber(), out.invoiceNumber())) {
      diffs.add(
          "invoiceNumber expected " + fixture.invoiceNumber() + " got " + out.invoiceNumber());
    }
    if (out.lines().size() != fixture.lines().size()) {
      diffs.add("line count expected " + fixture.lines().size() + " got " + out.lines().size());
      return diffs;
    }
    for (int i = 0; i < fixture.lines().size(); i++) {
      ExpectedLine expected = fixture.lines().get(i);
      PdfExtractedLine actual = out.lines().get(i);
      compare(diffs, i, "stockCode", expected.stockCode(), actual.stockCode());
      compare(diffs, i, "quantity", expected.quantity(), actual.quantity());
      compare(diffs, i, "uom", expected.uom(), actual.uom());
      compare(diffs, i, "unitQuantity", expected.unitQuantity(), actual.unitQuantity());
      compare(diffs, i, "packSize", expected.packSize(), actual.packSize());
      compare(diffs, i, "wetAmount", expected.wetAmount(), actual.wetAmount());
    }
    return diffs;
  }

  private static void compare(
      List<String> diffs, int lineIndex, String field, Object expected, Object actual) {
    if (!java.util.Objects.equals(expected, actual)) {
      diffs.add("line " + lineIndex + " " + field + " expected " + expected + " got " + actual);
    }
  }

  private List<Fixture> loadFixtures() throws IOException {
    JsonNode root =
        mapper.readTree(new ClassPathResource("invoice-pdf-eval/fixtures.json").getInputStream());
    List<Fixture> fixtures = new ArrayList<>();
    for (JsonNode node : root) {
      List<ExpectedLine> lines = new ArrayList<>();
      node.path("lines")
          .forEach(
              line ->
                  lines.add(
                      new ExpectedLine(
                          nullableText(line, "stockCode"),
                          nullableDecimal(line, "quantity"),
                          nullableText(line, "uom"),
                          nullableDecimal(line, "unitQuantity"),
                          nullableDecimal(line, "packSize"),
                          nullableDecimal(line, "wetAmount"))));
      fixtures.add(
          new Fixture(
              node.path("name").asText(),
              node.path("text").asText(),
              nullableText(node, "invoiceNumber"),
              lines));
    }
    return fixtures;
  }

  private static String nullableText(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private static BigDecimal nullableDecimal(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).decimalValue() : null;
  }

  private String renderReport(List<Result> results) {
    StringBuilder report = new StringBuilder();
    report.append("# Invoice PDF extraction — Live-LLM Eval\n\n");
    report.append("- **Run:** ").append(Instant.now()).append('\n');
    long passed = results.stream().filter(r -> r.diff().isEmpty()).count();
    report
        .append("- **Fixtures passed:** ")
        .append(passed)
        .append('/')
        .append(results.size())
        .append("\n\n");
    for (Result r : results) {
      report.append("## ").append(r.fixture().name()).append('\n');
      if (r.diff().isEmpty()) {
        report.append("- exact match\n\n");
      } else {
        for (String d : r.diff()) {
          report.append("- ").append(d).append('\n');
        }
        report.append('\n');
      }
    }
    return report.toString();
  }

  private record Fixture(
      String name, String text, String invoiceNumber, List<ExpectedLine> lines) {}

  private record ExpectedLine(
      String stockCode,
      BigDecimal quantity,
      String uom,
      BigDecimal unitQuantity,
      BigDecimal packSize,
      BigDecimal wetAmount) {}

  private record Result(Fixture fixture, PdfExtractedInvoice out, List<String> diff) {}
}
