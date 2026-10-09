package com.goldys.platform.application;

import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import java.util.List;
import org.springframework.stereotype.Service;

/** Implements the semantic {@link ConnectorHealthQuery} over the ingestion ledger. */
@Service
public class ConnectorHealthService implements ConnectorHealthQuery {
  private final IngestionService ingestion;

  public ConnectorHealthService(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @Override
  public List<ConnectorHealth> health() {
    return ingestion.latestRunPerConnector().stream()
        .map(
            r ->
                new ConnectorHealth(r.sourceSystem(), r.connectorName(), r.startedAt(), r.status()))
        .toList();
  }
}
