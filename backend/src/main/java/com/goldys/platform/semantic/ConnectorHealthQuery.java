package com.goldys.platform.semantic;

import java.util.List;

/**
 * Business read over connector freshness. Implemented in {@code application} from the ingestion
 * ledger.
 */
public interface ConnectorHealthQuery {
  List<ConnectorHealth> health();
}
