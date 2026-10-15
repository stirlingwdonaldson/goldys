package com.goldys.platform.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The public shape of a forecast run's diagnostic payload, mirroring the forecasting runtime's
 * camelCase wire object field-for-field. The payload is persisted as a raw JSONB string (see {@code
 * ForecastRun#diagnostics()}); this record is the parsed, typed view served to the REST API.
 *
 * <p>Every field beyond {@code modelVersion} is nullable: the XGBoost twin may be absent, a metric
 * block may be {@code null} when that model could not be trained, and numeric features may be
 * missing. Consumers should treat a missing block (e.g. {@link #holdoutFood()} == {@code null}) as
 * "no estimate", never a failed run.
 */
public record ForecastDiagnosticsDto(
    String modelVersion,
    Integer trainedRows,
    Boolean reliable,
    List<String> warnings,
    List<String> features,
    List<String> shareFeatures,
    AccuracyMetric holdoutNetXgboost,
    AccuracyMetric holdoutNetBaseline,
    AccuracyMetric holdoutFood,
    AccuracyMetric holdoutBev,
    AccuracyMetric cvNetXgboost,
    AccuracyMetric cvNetBaseline,
    Boolean beatsBaseline,
    List<Importance> importance,
    Backtest backtest) {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Parses a persisted diagnostics JSON string into a typed DTO, or returns {@code null} when the
   * payload is absent, blank, or not valid JSON. A malformed payload must never fail the run read,
   * so any parse error degrades to {@code null} rather than throwing.
   */
  public static ForecastDiagnosticsDto fromJson(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      JsonNode root = MAPPER.readTree(json);
      if (root == null || root.isNull()) {
        return null;
      }
      return new ForecastDiagnosticsDto(
          text(root.get("modelVersion")),
          integer(root.get("trainedRows")),
          bool(root.get("reliable")),
          strings(root.get("warnings")),
          strings(root.get("features")),
          strings(root.get("shareFeatures")),
          accuracy(root.get("holdoutNetXgboost")),
          accuracy(root.get("holdoutNetBaseline")),
          accuracy(root.get("holdoutFood")),
          accuracy(root.get("holdoutBev")),
          accuracy(root.get("cvNetXgboost")),
          accuracy(root.get("cvNetBaseline")),
          bool(root.get("beatsBaseline")),
          importance(root.get("importance")),
          backtest(root.get("backtest")));
    } catch (Exception malformed) {
      return null;
    }
  }

  /** Holdout/CV accuracy for one model: mean-absolute-error, RMSE, MAPE, and the sample size. */
  public record AccuracyMetric(BigDecimal mae, BigDecimal rmse, BigDecimal mape, Integer n) {}

  /** One feature's gain-based importance in the net model. */
  public record Importance(String feature, BigDecimal importance) {}

  /** The walk-forward backtest: per-day rows, weekday error summary, residuals, and worst days. */
  public record Backtest(
      List<BacktestRow> rows,
      List<WeekdayError> byWeekday,
      BigDecimal residualMean,
      BigDecimal residualStd,
      List<BacktestRow> worstDays) {}

  /** One backtest day: actual vs predicted net, the error, and the food/bev split. */
  public record BacktestRow(
      LocalDate date,
      String day,
      BigDecimal actualNet,
      BigDecimal predictedNet,
      BigDecimal error,
      BigDecimal absPctError,
      BigDecimal actualFood,
      BigDecimal predictedFood,
      BigDecimal actualBev,
      BigDecimal predictedBev,
      BigDecimal baselineNet) {}

  /** One weekday's aggregated backtest error. */
  public record WeekdayError(
      String day,
      Integer n,
      BigDecimal mae,
      BigDecimal mape,
      BigDecimal meanActual,
      BigDecimal meanPredicted) {}

  private static AccuracyMetric accuracy(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    return new AccuracyMetric(
        decimal(node.get("mae")),
        decimal(node.get("rmse")),
        decimal(node.get("mape")),
        integer(node.get("n")));
  }

  private static List<Importance> importance(JsonNode node) {
    if (node == null || node.isNull() || !node.isArray()) {
      return null;
    }
    List<Importance> rows = new ArrayList<>(node.size());
    for (JsonNode entry : node) {
      rows.add(new Importance(text(entry.get("feature")), decimal(entry.get("importance"))));
    }
    return rows;
  }

  private static Backtest backtest(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    return new Backtest(
        backtestRows(node.get("rows")),
        weekdayErrors(node.get("byWeekday")),
        decimal(node.get("residualMean")),
        decimal(node.get("residualStd")),
        backtestRows(node.get("worstDays")));
  }

  private static List<BacktestRow> backtestRows(JsonNode node) {
    if (node == null || node.isNull() || !node.isArray()) {
      return null;
    }
    List<BacktestRow> rows = new ArrayList<>(node.size());
    for (JsonNode entry : node) {
      rows.add(
          new BacktestRow(
              date(entry.get("date")),
              text(entry.get("day")),
              decimal(entry.get("actualNet")),
              decimal(entry.get("predictedNet")),
              decimal(entry.get("error")),
              decimal(entry.get("absPctError")),
              decimal(entry.get("actualFood")),
              decimal(entry.get("predictedFood")),
              decimal(entry.get("actualBev")),
              decimal(entry.get("predictedBev")),
              decimal(entry.get("baselineNet"))));
    }
    return rows;
  }

  private static List<WeekdayError> weekdayErrors(JsonNode node) {
    if (node == null || node.isNull() || !node.isArray()) {
      return null;
    }
    List<WeekdayError> rows = new ArrayList<>(node.size());
    for (JsonNode entry : node) {
      rows.add(
          new WeekdayError(
              text(entry.get("day")),
              integer(entry.get("n")),
              decimal(entry.get("mae")),
              decimal(entry.get("mape")),
              decimal(entry.get("meanActual")),
              decimal(entry.get("meanPredicted"))));
    }
    return rows;
  }

  private static List<String> strings(JsonNode node) {
    if (node == null || node.isNull() || !node.isArray()) {
      return null;
    }
    List<String> values = new ArrayList<>(node.size());
    for (JsonNode entry : node) {
      values.add(entry.isNull() ? null : entry.asText());
    }
    return values;
  }

  private static BigDecimal decimal(JsonNode node) {
    return node == null || node.isNull() ? null : node.decimalValue();
  }

  private static Integer integer(JsonNode node) {
    return node == null || node.isNull() ? null : node.intValue();
  }

  private static Boolean bool(JsonNode node) {
    return node == null || node.isNull() ? null : node.asBoolean();
  }

  private static String text(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText();
  }

  private static LocalDate date(JsonNode node) {
    return node == null || node.isNull() ? null : LocalDate.parse(node.asText());
  }
}
