package com.goldys.platform.ingestion;

import com.goldys.platform.connectors.ctb.CtbClient;
import com.goldys.platform.connectors.ctb.CtbDetailBackfillConnector;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off trigger for the CTB detail backfill: runs {@code CtbDetailBackfillConnector} on startup,
 * enabled per deploy with {@code app.ctb-detail-backfill=true} (off by default). Unset the flag
 * after the run — re-running appends duplicate raw records (the ledger is append-only).
 *
 * <p>Lives in {@code ingestion} (not {@code connectors.ctb}) so it can call the package-private
 * {@link ConnectorRunner}. The connector is constructed here rather than registered as a Spring
 * bean because {@code IngestionService} keys its connector map by {@code sourceSystem}, and a
 * second {@code CTB} bean would collide with the scheduled {@code CtbConnector}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.ctb-detail-backfill", havingValue = "true")
public class CtbDetailBackfillRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(CtbDetailBackfillRunner.class);

  private final ConnectorRunner connectorRunner;
  private final Supplier<CtbDetailBackfillConnector> connectorFactory;

  // @Autowired: this class has two constructors (the package-private one is a test seam), so Spring
  // needs the annotation to know which one to use for bean creation.
  @Autowired
  public CtbDetailBackfillRunner(
      ConnectorRunner connectorRunner,
      @Value("${ctb.base-url:https://web.cookingthebooks.com.au}") String baseUrl,
      @Value("${ctb.email:}") String email,
      @Value("${ctb.password:}") String password) {
    this(
        connectorRunner,
        () -> new CtbDetailBackfillConnector(new CtbClient(baseUrl), email, password));
  }

  CtbDetailBackfillRunner(
      ConnectorRunner connectorRunner, Supplier<CtbDetailBackfillConnector> connectorFactory) {
    this.connectorRunner = connectorRunner;
    this.connectorFactory = connectorFactory;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.info("Starting one-off CTB detail backfill (connector ctb-detail-backfill)");
    connectorRunner.run(connectorFactory.get(), null);
  }
}
