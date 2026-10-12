package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the pull-run guard is atomic: many threads calling {@code runConnector} for the same
 * source at once must start exactly one run. A check-then-act race (cron tick vs "Run now") would
 * otherwise start several. The extra connector is a test-only stub whose {@code fetch} sleeps so
 * the first run stays {@code RUNNING} while the other threads race.
 */
@SpringBootTest
@Import({PostgresContainerConfiguration.class, RunConnectorConcurrencyIntegrationTest.Stub.class})
class RunConnectorConcurrencyIntegrationTest {

  private static final String SOURCE = "TEST";

  @Autowired IngestionService ingestion;
  @Autowired JdbcTemplate jdbc;

  @TestConfiguration(proxyBeanMethods = false)
  static class Stub {
    @Bean
    SourceConnector testConnector() {
      return new SourceConnector() {
        @Override
        public String sourceSystem() {
          return SOURCE;
        }

        @Override
        public String connectorName() {
          return "test-connector";
        }

        @Override
        public void fetch(String watermark, IngestionSink sink) {
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      };
    }
  }

  @BeforeEach
  void clean() {
    jdbc.update("delete from ingestion_run where source_system = ?", SOURCE);
  }

  @Test
  void concurrentRunConnectorStartsExactlyOneRun() throws Exception {
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CyclicBarrier barrier = new CyclicBarrier(threads);
    List<Future<IngestionRunSummary>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      futures.add(
          pool.submit(
              () -> {
                barrier.await();
                return ingestion.runConnector(SOURCE);
              }));
    }
    for (Future<IngestionRunSummary> future : futures) {
      future.get(); // let any failure propagate
    }
    pool.shutdown();

    Integer count =
        jdbc.queryForObject(
            "select count(*) from ingestion_run where source_system = ?", Integer.class, SOURCE);
    assertThat(count).isEqualTo(1);
  }
}
