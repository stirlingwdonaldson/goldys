package com.goldys.platform.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Central naming for the application's operational metrics, so Prometheus/Grafana see a small,
 * controlled cardinality surface rather than ad-hoc meter names scattered through the code.
 */
@Component
public class OperationalMetrics {
  private final MeterRegistry registry;

  public OperationalMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public Timer.Sample start() {
    return Timer.start(registry);
  }

  public void stopProjection(Timer.Sample sample, String entityType) {
    sample.stop(registry.timer("goldys.projection.duration", "entity", entityType));
  }

  public void stopConnector(Timer.Sample sample, String source) {
    sample.stop(registry.timer("goldys.connector.duration", "source", source));
  }

  public void connectorFailure(String source) {
    registry.counter("goldys.connector.failures", "source", source).increment();
  }

  public void payloadIngested(String source) {
    registry.counter("goldys.ingestion.payloads", "source", source).increment();
  }

  public void stopTool(Timer.Sample sample, String tool) {
    sample.stop(registry.timer("goldys.ai.tool.duration", "tool", tool));
  }

  public void toolFailure(String tool) {
    registry.counter("goldys.ai.tool.failures", "tool", tool).increment();
  }
}
