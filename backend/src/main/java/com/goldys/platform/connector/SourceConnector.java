package com.goldys.platform.connector;

import com.goldys.platform.raw.SourceSystem;

/**
 * Common port every vendor adapter implements (system-context.md's "connector isolation" invariant,
 * spec Requirement 4). Adding or swapping a source should never require changes outside its adapter
 * - if implementing a new connector forces a change here, the port is leaking a vendor-specific
 * concern and should be reconsidered.
 *
 * <p>No connectors implement this yet in the scaffold. Requirement 4 lists the confirmed ingestion
 * path per in-scope source (Lightspeed: back-office scrape, CTB: CSV/SFTP export, OpenTable:
 * scripted browser pull, Deputy: OAuth REST API) - each becomes one class implementing this
 * interface.
 */
public interface SourceConnector {

  SourceSystem sourceSystem();

  /**
   * Fetch and persist new/changed raw records since the last successful run. Implementations MUST
   * report failures via IngestionFailure (spec Requirement 6) rather than throwing silently or
   * simply returning no rows.
   */
  IngestionRunResult fetch();
}
