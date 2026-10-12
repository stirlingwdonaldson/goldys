package com.goldys.platform.forecasting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A persisted asynchronous forecast run: the target metric, the past-only covariates that feed the
 * model, and — once the TimesFM inference service has responded — the quantile series and model
 * metadata. The series is stored as JSONB on the row because compare-to-actuals always loads the
 * whole series.
 */
@Entity
@Table(name = "forecast_run")
public class ForecastRun {

  @Id private UUID id;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false, length = 255)
  private String createdBy;

  @Column(name = "target_metric", nullable = false)
  private String targetMetric;

  @Column(name = "target_dimension", length = 64)
  private String targetDimension;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "covariates", nullable = false, columnDefinition = "jsonb")
  private List<String> covariates;

  @Column(name = "history_from", nullable = false)
  private LocalDate historyFrom;

  @Column(name = "history_to", nullable = false)
  private LocalDate historyTo;

  @Column(name = "horizon_days", nullable = false)
  private int horizonDays;

  @Column(name = "grain", nullable = false, length = 16)
  private String grain;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ForecastRunStatus status;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "model_version")
  private String modelVersion;

  @Column(name = "elapsed_ms")
  private Long elapsedMs;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "forecast_series", columnDefinition = "jsonb")
  private List<ForecastDay> series;

  protected ForecastRun() {}

  private ForecastRun(
      UUID id,
      Instant createdAt,
      String createdBy,
      String targetMetric,
      String targetDimension,
      List<String> covariates,
      LocalDate historyFrom,
      LocalDate historyTo,
      int horizonDays,
      String grain,
      ForecastRunStatus status,
      String errorMessage,
      String modelVersion,
      Long elapsedMs,
      List<ForecastDay> series) {
    this.id = Objects.requireNonNull(id, "id");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.targetMetric = Objects.requireNonNull(targetMetric, "targetMetric");
    this.targetDimension = targetDimension;
    this.covariates = List.copyOf(covariates);
    this.historyFrom = Objects.requireNonNull(historyFrom, "historyFrom");
    this.historyTo = Objects.requireNonNull(historyTo, "historyTo");
    this.horizonDays = horizonDays;
    this.grain = Objects.requireNonNull(grain, "grain");
    this.status = Objects.requireNonNull(status, "status");
    this.errorMessage = errorMessage;
    this.modelVersion = modelVersion;
    this.elapsedMs = elapsedMs;
    this.series = series == null ? List.of() : List.copyOf(series);
  }

  public static ForecastRun create(
      String targetMetric,
      List<String> covariates,
      LocalDate historyFrom,
      LocalDate historyTo,
      int horizonDays,
      String createdBy,
      Instant now) {
    return new ForecastRun(
        UUID.randomUUID(),
        now,
        createdBy,
        targetMetric,
        null,
        covariates,
        historyFrom,
        historyTo,
        horizonDays,
        "DAY",
        ForecastRunStatus.QUEUED,
        null,
        null,
        null,
        List.of());
  }

  /** Moves a queued run to the in-flight state once the inference call has been dispatched. */
  public void markRunning() {
    this.status = ForecastRunStatus.RUNNING;
  }

  /** Records a successful forecast: the quantile series plus the model and timing metadata. */
  public void markSucceeded(List<ForecastDay> series, String modelVersion, long elapsedMs) {
    this.status = ForecastRunStatus.SUCCEEDED;
    this.series = List.copyOf(series);
    this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion");
    this.elapsedMs = elapsedMs;
  }

  /** Records a failed forecast with an operator-facing message (never credentials or payloads). */
  public void markFailed(String message) {
    this.status = ForecastRunStatus.FAILED;
    this.errorMessage = Objects.requireNonNull(message, "message");
  }

  public UUID id() {
    return id;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String createdBy() {
    return createdBy;
  }

  public String targetMetric() {
    return targetMetric;
  }

  public String targetDimension() {
    return targetDimension;
  }

  public List<String> covariates() {
    return covariates;
  }

  public LocalDate historyFrom() {
    return historyFrom;
  }

  public LocalDate historyTo() {
    return historyTo;
  }

  public int horizonDays() {
    return horizonDays;
  }

  public String grain() {
    return grain;
  }

  public ForecastRunStatus status() {
    return status;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public String modelVersion() {
    return modelVersion;
  }

  public Long elapsedMs() {
    return elapsedMs;
  }

  public List<ForecastDay> series() {
    return series;
  }
}
