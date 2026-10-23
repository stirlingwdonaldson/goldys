package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.support.PostgresContainerConfiguration;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
@Transactional
class SaleItemPriceQueryIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;
  @Autowired CanonicalSaleItemQuery query;
  @Autowired IngestionService ingestion;

  @Test
  void priceInputsKeepEveryCurrentObservationAndEvidenceWithoutManagingSaleEntities() {
    var raw =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "price-test",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            new byte[] {1},
            "UTF-8",
            "price-test");
    entityManager.flush();
    jdbc.update(
        """
        insert into canonical_sale_item (id,logical_entity_id,source_system,source_record_ref,
          raw_record_id,valid_from,recorded_at,trading_date,item_name,sold_price_inc_tax,cost_inc_tax,
          superseded_at)
        select gen_random_uuid(),gen_random_uuid(),'LIGHTSPEED','price-test:' || n,
          ?,now(),now(),date '2026-10-21','Steak Frites',28,0,
          case when n=1001 then now() else null end
        from generate_series(1,1001) n
        """,
        raw);
    entityManager.clear();

    var prices =
        query.currentSaleItemPrices().stream()
            .filter(p -> p.evidence().rawRecordId().equals(raw))
            .toList();

    assertThat(prices).hasSize(1000);
    assertThat(prices)
        .allSatisfy(
            p -> {
              assertThat(p.itemName()).isEqualTo("Steak Frites");
              assertThat(p.soldPriceIncTax()).isEqualByComparingTo("28");
              assertThat(p.costIncTax()).isZero();
              assertThat(p.tradingDate()).isEqualTo(LocalDate.of(2026, 10, 21));
              assertThat(p.evidence().rawRecordId()).isEqualTo(raw);
              assertThat(p.evidence().ingestionRunId()).isNotNull();
              assertThat(p.evidence().sha256()).hasSize(64);
            });
    assertThat(prices.stream().flatMap(p -> p.evidence().entityRefs().stream()))
        .hasSize(1000)
        .doesNotHaveDuplicates()
        .doesNotContain("price-test:1001");
    // Managed sale history makes every subsequent evidence query dirty-check the entire history.
    assertThat(entityManager.unwrap(Session.class).getStatistics().getEntityCount()).isZero();
  }
}
