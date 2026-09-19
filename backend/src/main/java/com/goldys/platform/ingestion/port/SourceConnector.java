package com.goldys.platform.ingestion.port;

/**
 * The one port every source integration implements.
 *
 * <p>Connectors are one-way: they read from a source and hand payloads to the sink. If adding a
 * source requires changing this interface, the interface is leaking a vendor concern.
 */
public interface SourceConnector {
  /** Stable source identifier recorded on every run, payload, and failure. */
  String sourceSystem();

  /** Identifies which adapter produced the data, so two adapters for one source stay separable. */
  String connectorName();

  /**
   * Fetches everything new since {@code watermark}, handing each payload to {@code sink}
   * immediately.
   *
   * @throws ConnectorFetchException for an expected, classified source failure
   */
  void fetch(String watermark, IngestionSink sink);
}
